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
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;


/**
 * Abstract base class for {@link DataFieldMaxValueIncrementer} implementations
 * which are based on identity columns in a sequence-like table.
 *
 * @author Juergen Hoeller
 * @author Thomas Risberg
 * @since 4.1.2
 */
public abstract class AbstractIdentityColumnMaxValueIncrementer extends AbstractColumnMaxValueIncrementer {

	/**
	 * Default constructor for bean property style usage.
	 * @see #setDataSource
	 * @see #setIncrementerName
	 * @see #setColumnName
	 */
	public AbstractIdentityColumnMaxValueIncrementer() {
	}

	public AbstractIdentityColumnMaxValueIncrementer(DataSource dataSource, String incrementerName, String columnName) {
		super(dataSource, incrementerName, columnName);
	}

	@Override
	protected long getNextKey() throws DataAccessException {
	   /*
		* Need to use straight JDBC code because we need to make sure that the insert and select
		* are performed on the same connection (otherwise we can't be sure that @@identity
		* returns the correct value)
		*/
		Connection con = DataSourceUtils.getConnection(getDataSource());
		Statement stmt = null;
		Long value = null;
		try {
			stmt = con.createStatement();
			DataSourceUtils.applyTransactionTimeout(stmt, getDataSource());
			stmt.executeUpdate(getIncrementStatement());
			ResultSet rs = stmt.executeQuery(getIdentityStatement());
			try {
				if (!rs.next()) {
					throw new DataAccessResourceFailureException("Identity statement failed after inserting");
				}
				value = rs.getLong(1);
			}
			finally {
				JdbcUtils.closeResultSet(rs);
			}
			stmt.executeUpdate(getDeleteStatement(value));
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Could not increment identity", ex);
		}
		finally {
			JdbcUtils.closeStatement(stmt);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
		return value;
	}


	/**
	 * Statement to use to increment the "sequence" value.
	 * @return the SQL statement to use
	 */
	protected abstract String getIncrementStatement();

	/**
	 * Statement to use to obtain the current identity value.
	 * @return the SQL statement to use
	 */
	protected abstract String getIdentityStatement();

	/**
	 * Statement to use to clean up "sequence" values.
	 * <p>The default implementation either deletes the entire range below
	 * the current maximum value, or the specifically generated values
	 * (starting with the lowest minus 1, just preserving the maximum value)
	 * - according to the {@link #isDeleteSpecificValues()} setting.
	 * @param values the currently generated key values
	 * (the number of values corresponds to {@link #getCacheSize()})
	 * @return the SQL statement to use
	 */
	protected String getDeleteStatement(long value) {
		StringBuilder sb = new StringBuilder(64);
		sb.append("delete from ").append(getIncrementerName()).append(" where ").append(getColumnName());
		sb.append(" < ").append(value);
		return sb.toString();
	}

}
