package com.example.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.incrementer.AbstractColumnMaxValueIncrementer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * This incrementer insures the delete is run in a new transaction to prevent deadlocks.
 * 
 * @author tyler.vangorder
 */
public class NestedTransactionOnDeleteValueIncrementer extends  AbstractColumnMaxValueIncrementer {

	private TransactionTemplate transactionTemplate;
	 
	/** The current cache of values */
	private long[] valueCache;

	/** The next id to serve from the value cache */
	private int nextValueIndex = -1;

	/**
	 * Default constructor for bean property style usage.
	 * @see #setDataSource
	 * @see #setIncrementerName
	 * @see #setColumnName
	 */
	public NestedTransactionOnDeleteValueIncrementer() {
	}

	/**
	 * Convenience constructor.
	 * @param dataSource the DataSource to use
	 * @param incrementerName the name of the sequence/table to use
	 * @param columnName the name of the column in the sequence table to use
	 */
	public NestedTransactionOnDeleteValueIncrementer(DataSource dataSource, String incrementerName, String columnName, PlatformTransactionManager transactionManager) {
		super(dataSource, incrementerName, columnName);
		transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	protected String getIncrementStatement() {
		return "insert into " + getIncrementerName() + " default values";
	}

	protected String getIdentityStatement() {
		return "select @@identity";
	}

	
	@Override
	protected synchronized long getNextKey() throws DataAccessException {
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
				final long[] values = this.valueCache;
				final Statement statement = stmt;
				transactionTemplate.execute(new TransactionCallbackWithoutResult() {
					
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus status) {
						// TODO Auto-generated method stub
						try {
							statement.executeUpdate(getDeleteStatement(values));
						} catch (SQLException e) {
							status.setRollbackOnly();
						}
					}
				});
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
}

