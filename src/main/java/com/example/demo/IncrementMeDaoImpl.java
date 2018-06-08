package com.example.demo;

import javax.sql.DataSource;

import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IncrementMeDaoImpl implements IncrementMeDao {

	private final DataFieldMaxValueIncrementer incrementer;
	
	public IncrementMeDaoImpl(DataSource dataSource) {
//		incrementer = new SqlServerMaxValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");
		incrementer = new CustomValueIncrementer(dataSource, "EXAMPLE_SEQ", "id");		
	}

	@Override
	@Transactional(readOnly=false, isolation=Isolation.SERIALIZABLE)
	public int getNextValue() {
		return incrementer.nextIntValue();
	}
	
}
