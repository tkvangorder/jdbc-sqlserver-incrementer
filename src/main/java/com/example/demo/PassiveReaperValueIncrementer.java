package com.example.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.incrementer.AbstractColumnMaxValueIncrementer;


/**
 * This incrementer sets up a reaper interval relative to the current time, when the getNextKey() method is called and we have exceeded the reap interval, 
 * this class will spin up a separate thread to delete all old values in the table. The fact that this is done in a different thread, means that it will also
 * be done in a separate transaction.
 * 
 * This does NOT leave a reaping thread running indefinitely.
 * 
 * @author tyler.vangorder
 *
 */
public class PassiveReaperValueIncrementer extends  AbstractColumnMaxValueIncrementer {

	private static final Logger logger = LoggerFactory.getLogger(PassiveReaperValueIncrementer.class);
	private long[] valueCache;

	/** The next id to serve from the value cache */
	private int nextValueIndex = -1;

	private int reaperInternalSeconds = 5;

	private long nextReapTime;
	 
	/**
	 * Default constructor for bean property style usage.
	 * @see #setDataSource
	 * @see #setIncrementerName
	 * @see #setColumnName
	 */
	public PassiveReaperValueIncrementer() {
		nextReapTime = getReapTime();
	}

	/**
	 * Convenience constructor.
	 * @param dataSource the DataSource to use
	 * @param incrementerName the name of the sequence/table to use
	 * @param columnName the name of the column in the sequence table to use
	 */
	public PassiveReaperValueIncrementer(DataSource dataSource, String incrementerName, String columnName) {
		super(dataSource, incrementerName, columnName);
	}

	protected String getIncrementStatement() {
		return "insert into " + getIncrementerName() + " default values";
	}

	protected String getIdentityStatement() {
		return "select @@identity";
	}

	
	@Override
	protected synchronized long getNextKey() {
		if (this.nextValueIndex < 0 || this.nextValueIndex >= getCacheSize()) {
			/*
			* Need to use straight JDBC code because we need to make sure that the insert and select
			* are performed on the same connection (otherwise we can't be sure that @@identity
			* returns the correct value)
			*/
			Connection con = DataSourceUtils.getConnection(getDataSource());
			Statement stmt = null;
			try {
				stmt = con.createStatement();
				DataSourceUtils.applyTransactionTimeout(stmt, getDataSource());
				this.valueCache = new long[getCacheSize()];
				this.nextValueIndex = 0;
				for (int i = 0; i < getCacheSize(); i++) {
					stmt.executeUpdate(getIncrementStatement());
					ResultSet rs = stmt.executeQuery(getIdentityStatement());
					try {
						if (!rs.next()) {
							throw new DataAccessResourceFailureException("Identity statement failed after inserting");
						}
						this.valueCache[i] = rs.getLong(1);
					}
					finally {
						JdbcUtils.closeResultSet(rs);
					}
				}
				if (System.currentTimeMillis() > nextReapTime) {
					//If the current time has exceeded the reap time, spin up a thread to delete the old values.
					logger.info("Reaping old values.");
					//We could inject a taskexecuter into this thing, but seems like that might be a more invasive change?
					Thread reapingThread = new Thread(new ReapOldValues(), "Incrementer " +  getIncrementerName()  +  " Reaping Thread");
					reapingThread.setDaemon(true);
					reapingThread.start();
					nextReapTime = getReapTime();
				}
			}
			catch (SQLException ex) {
				throw new DataAccessResourceFailureException("Could not increment identity", ex);
			}
			finally {
				JdbcUtils.closeStatement(stmt);
				DataSourceUtils.releaseConnection(con, getDataSource());
			}
		}
		return this.valueCache[this.nextValueIndex++];
	}

	protected String getDeleteStatement(long[] values) {
		StringBuilder sb = new StringBuilder(64);
		sb.append("delete from ").append(getIncrementerName()).append(" where ").append(getColumnName());
		sb.append(" in (").append(values[0] - 1);
		for (int i = 0; i < values.length - 1; i++) {
			sb.append(", ").append(values[i]);
		}
		sb.append(")");
		return sb.toString();
	}

	private long getReapTime() {
		return System.currentTimeMillis() + (reaperInternalSeconds * 1000);
	}
	
	private class ReapOldValues implements Runnable {
		
		@Override
		public void run() {
			Connection con = DataSourceUtils.getConnection(getDataSource());
			Statement stmt = null;
			try {
				stmt = con.createStatement();
				DataSourceUtils.applyTransactionTimeout(stmt, getDataSource());
				stmt.executeUpdate("DELETE FROM " + getIncrementerName());
			}
			catch (SQLException ex) {
				throw new DataAccessResourceFailureException("Could not delete old identity values", ex);
			}
			finally {
				JdbcUtils.closeStatement(stmt);
				DataSourceUtils.releaseConnection(con, getDataSource());
			}			
		}
	}
	
}

