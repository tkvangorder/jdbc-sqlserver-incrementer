# Sandbox to demonstrate/fix deadlocking issue with SqlServerMaxValueIncrementer

First off, I want to make sure I state, Spring, Spring Batch, Spring Cloud Task, and Spring Cloud Data Flow are amazing technologies that help us get our jobs done more quickly and we are internally greatful for all the hard work the Spring team puts into those projects!

# Background

We had a legacy scheduler that was running 100+ tasks in a single VM and we are now in the process of breaking each task out into a Spring Cloud Task workflow and
have our scheduler launch these through the Spring Cloud Data Flow API. This is working great, except that we ran into a strange issue with a deadlock that was
occurring when we had multiple tasks running at the same time. These tasks all use spring batch and are all pointed to the same database to host the batch and task
meta-tables. The deadlock was occurring when the batch code was attempting to get the next sequence from the database.

We opend up an issue here: [SPR-16886](https://jira.spring.io/browse/SPR-16886). We have also seen others that have encountered this same issue: [BATCH-2147](https://jira.spring.io/browse/BATCH-2147).

To triage what was going on, I created this sandbox to allow me to test various scenarios. 

## The Issue

We use SqlServer and prior to SqlServer 2012, that DBMS did NOT have a concept of a sequence. To work around this issue, Spring has an abstract class that uses
a table with a single identity column. The stragety in this class is to insert a record into this table, select back the new value and then to delete all
previous records in the table. The problem with this approach is that if you have multiple clients attempting to do this concurrently, you will end up deadlocking.

1. Why doesn't this happen when running a single instance?

org.springframework.jdbc.support.incrementer.AbstractIdentityColumnMaxValueIncrementer.getNextKey() is synchronized, so if you are sharing an incrementer, only
one thread at a time can get into this method.

2. The second variable that must be in play is that the incrementer must be used within the scope of a transaction where both the insert and the delete are going to occur atomically. My theory here is that because there are so few rows in the table that SqlServer escalates the lock to a table lock. I suspect, but I am not sure, that other databases that use this approach may encounter similar problems.

## Building and Running This Project

1. There is a docker-compose file that will run sql server on your local machine, this project assumes it is talking to this database. Make sure to "docker-compose up" to launch your database.
2. This maven project can be build via "mvn clean install" or imported into your favorite IDE.
3. This application just has a simple command-line runner that launches a bunch of threads.

## Notes:

- ExampleDatabaseInitializer is used to create the sequence table (and a real sequence) on startup, see "src/main/resources/com/example/demo/example-sqlserver.sql"
- "src/main/resources/application.yml" has configuration settings that can be tweaked (number of threads, number of samples per thread, and which increment strategy to use.)
- The meat of this project's logic is found in the IncrementMeDaoImpl and each of the "incrementer" variants.

## Strategies
I have created a couple of variants of the table incrementer (and one that uses a sequence) to address the deadlocking issue. The strategy used by this project can be set in the application.yml file.

### DEFAULT_SHARED
Use the default SQLServer incrementer, this will work in a single application instance because the getNextKey() method is synchronized.
### DEFAULT_NOT_SHARED
Use the default SQLServer incrementer, but return a new instance each time, this will fail with deadlock issues.
### NESTED_TRANSACTION_ON_DELETE
This incrementer runs the delete in a nested transaction, but requires the platform transaction manager to be injected into it. This works nicely, but might require more coding changes to support.

### PASSIVE_REAPER
This incrementer keeps track of a reaper interval (relative to the current time), if getNextKey is called and the current time exceeds the last recorded reaping interval, a separate thread will be spun up for delete. Since this is in a different thread, it will run in a separate transaction. This strategy will leave a small number of records in the increment table. I've intentionally not used a task executor so that we do not have to inject it into the incrementer. 

### SEQUENCE
This incrementer just uses the a sequence instead of the table strategy. This will work with any SQL SERVER version greater than 2012 and is really the preferred approach. Not sure if Spring can just deprecate the use of SQL SERVER older than 2012.....which might be reasonable, seeing as how SQL SERVER 2008 is 10 years old.
