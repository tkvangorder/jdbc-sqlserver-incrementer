package com.example.demo;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.SqlServerMaxValueIncrementer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConfigurationProperties("example")
public class IncrementMeDaoImpl implements IncrementMeDao {

	private final DataSource dataSource;

	//Only used with Nested transaction stategy.
	private final PlatformTransactionManager transactionManager;

	private DataFieldMaxValueIncrementer incrementer;
	private IncrementStrategy incrementStrategy = IncrementStrategy.DEFAULT_NOT_SHARED;
	
	public IncrementMeDaoImpl(DataSource dataSource, PlatformTransactionManager transactionManager) {
		this.dataSource = dataSource;
		this.transactionManager = transactionManager;
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
	
		if (incrementStrategy != IncrementStrategy.DEFAULT_NOT_SHARED && incrementer != null) {
			return incrementer;
		}

		switch (incrementStrategy) {
			case DEFAULT_NOT_SHARED :
			case DEFAULT_SHARED:
				incrementer = new SqlServerMaxValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");
				break;			
			case NESTED_TRANSACTION_ON_DELETE :
				incrementer = new NestedTransactionOnDeleteValueIncrementer(dataSource, "EXAMPLE_SEQ", "id", transactionManager);
				break;							
			case PASSIVE_REAPER :
				incrementer = new SqlServerMaxValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");
				break;				
			case SEQUENCE :
				incrementer = new SqlServerSequenceMaxValueIncrementer(dataSource, "EXAMPLE_REAL_SEQUENCE");
				break;
		}
		return incrementer;
	}

	public void setIncrementStrategy(IncrementStrategy incrementStrategy) {
		this.incrementStrategy = incrementStrategy;
	}

	
}
