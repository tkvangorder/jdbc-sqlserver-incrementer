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

- ExampleDatabaseInitializer is used to create the sequence table on startup, see "src/main/resources/com/example/demo/example-sqlserver.sql"
- "src/main/resources/application.yml" has configuration settings that can be tweaked (number of threads, number of samples per thread, and most importantly a flag to switch the incremeneter strategy from shared to non-shared.)
- if you set "use-shared-incrementer to "true" and run the application, it will complete successfully because of the synchronization around "getNextKey()". However, if you run two instances of the application at the same time, you will encounter the deadlocking issue.
- if you set "use-shared-incrementer to "false", you will encounter the deadlock almost immediately.

## Next Steps

- I think we can come up with a better strategy around the delete call, such that multiple clients will not step on each other...