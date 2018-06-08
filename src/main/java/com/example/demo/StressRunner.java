package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.task.TaskExecutor;

@ConfigurationProperties("example")
public class StressRunner implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(StressRunner.class);

	private int stressTestThreadCount = 12;
	private int samplesPerThread = 100;
	
	private final TaskExecutor taskExecutor;
	private final IncrementMeDao incrementMeDao;
	
	public StressRunner(TaskExecutor taskExecutor, IncrementMeDao incrementMeDao) {
		this.taskExecutor = taskExecutor;
		this.incrementMeDao = incrementMeDao;
	}
	
	@Override
	public void run(String... args) throws Exception {

		logger.info("Starting {} Threads",  stressTestThreadCount); 
		for (int index = 0; index < stressTestThreadCount; index++) {
			taskExecutor.execute(new CallIncrementer(samplesPerThread));
		}

	}


	private class CallIncrementer implements Runnable {

		private final int sampleCount;
		private CallIncrementer(int sampleCount) {
			this.sampleCount = sampleCount;
		}
		@Override
		public void run() {
			for (int index=0; index < sampleCount; index++) {
				logger.info("Got Value : {}", incrementMeDao.getNextValue()); 
//				try {
//					Thread.sleep(new Random().nextInt(10));
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
			}			
		}
	}


	public void setStressTestThreadCount(int stressTestThreadCount) {
		this.stressTestThreadCount = stressTestThreadCount;
	}

	public void setSamplesPerThread(int samplesPerThread) {
		this.samplesPerThread = samplesPerThread;
	}
}
