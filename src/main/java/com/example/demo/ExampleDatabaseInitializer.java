package com.example.demo;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.AbstractDataSourceInitializer;
import org.springframework.boot.jdbc.DataSourceInitializationMode;
import org.springframework.core.io.ResourceLoader;

public class ExampleDatabaseInitializer extends AbstractDataSourceInitializer {


	@Value("${example.create-database:true}")
	private boolean createDatabase = true;

	private static final String SCHEMA_SCRIPT_LOCATION = "classpath:com/example/demo/example-@@platform@@.sql";

	public ExampleDatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
		super(dataSource, resourceLoader);
	}

	@Override
	protected String getSchemaLocation() {
		return SCHEMA_SCRIPT_LOCATION;
	}

	@Override
	protected DataSourceInitializationMode getMode() {
		if (createDatabase) {
			return DataSourceInitializationMode.ALWAYS;
		} else {
			return DataSourceInitializationMode.NEVER;
		}
	}
}
