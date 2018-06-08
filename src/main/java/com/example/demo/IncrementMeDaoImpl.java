package com.example.demo;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.SqlServerMaxValueIncrementer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IncrementMeDaoImpl implements IncrementMeDao {

	private final DataSource dataSource;
	private DataFieldMaxValueIncrementer incrementer;
	
	@Value("${example.use-shared-incrementer:false}")
	private boolean useSharedIncrementer = false;
	
	public IncrementMeDaoImpl(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	// Pre-condition to get the deadlock error:
	// - The incrementer must be used from the context of transaction!
	//
	// - If you are using a shared incrementer, you must run multiple instances of the application to see the deadlocking issues.
	// - If you are NOT using a shared incrementer, you will see the deadlock within the single running instance. 
	@Override
	@Transactional(readOnly=false, isolation=Isolation.READ_COMMITTED)
	public int getNextValue() {
		return getIncrementer().nextIntValue();
	}

	private DataFieldMaxValueIncrementer getIncrementer() {
	
		if (useSharedIncrementer && incrementer != null) {
			return incrementer;
		}
		incrementer = new SqlServerMaxValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");
		//incrementer = new CustomValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");	
		return incrementer;
	}
	
}
