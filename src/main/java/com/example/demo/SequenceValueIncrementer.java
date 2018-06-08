package com.example.demo;

import javax.sql.DataSource;

import org.springframework.jdbc.support.incrementer.AbstractSequenceMaxValueIncrementer;

/**
 * This incrementer just uses the native sequences available in SqlServer 2012 or later.
 * 
 * This is really the preferred method.
 * 
 * @author tyler.vangorder
 *
 */
public class SequenceValueIncrementer extends AbstractSequenceMaxValueIncrementer {

	public SequenceValueIncrementer(DataSource dataSource, String incrementerName) {
		super(dataSource, incrementerName);
	}

	@Override
	protected String getSequenceQuery() {
		return "select NEXT VALUE for " + getIncrementerName();
	}
}
