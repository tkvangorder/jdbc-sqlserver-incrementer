package com.example.demo;

public enum IncrementStrategy {

	DEFAULT_SHARED,
	DEFAULT_NOT_SHARED,
	NESTED_TRANSACTION_ON_DELETE,
	PASSIVE_REAPER,
	SEQUENCE;
}
