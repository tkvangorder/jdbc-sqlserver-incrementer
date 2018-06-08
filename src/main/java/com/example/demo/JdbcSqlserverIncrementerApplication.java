package com.example.demo;


import javax.sql.DataSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class JdbcSqlserverIncrementerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JdbcSqlserverIncrementerApplication.class, args);
	}
	
	@Bean
	ExampleDatabaseInitializer exampleDatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader)  {
		return new ExampleDatabaseInitializer(dataSource, resourceLoader);
	}
	
    @Bean(destroyMethod="shutdown")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(80);
        executor.setMaxPoolSize(80);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("StressTest-");
        executor.initialize();
        return executor;
    }	

    @Bean
    public StressRunner stressRunner(DataSource dataSource, IncrementMeDao incrementDao) {
    	return new StressRunner(taskExecutor(), incrementDao);
    }
}
