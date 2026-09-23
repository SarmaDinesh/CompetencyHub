package com.example.CompetencyHub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Both annotations work the same way as @EnableAspectJAutoProxy did back in Module 2: they
 * tell Spring to wrap the beans that carry @Scheduled or @Async in a proxy.
 *
 * Which means the proxy self-invocation gotcha applies here too, exactly as it did with the
 * timing aspect. A method inside a bean calling its own @Async method calls it directly on
 * `this`, bypassing the proxy, and it runs synchronously with no error and no warning. Same
 * bug, third context. Whenever a Spring annotation changes what happens *around* a method
 * call, it only works when the call arrives from outside the object.
 *
 * Putting both on a dedicated @Configuration rather than on the main class is a small thing,
 * but it means `git log config/SchedulingConfig.java` tells you when async execution entered
 * the app. Annotations piled on @SpringBootApplication are invisible in history.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    /**
     * An explicit pool for jobs. Named "jobExecutor" so @Async("jobExecutor") finds it.
     *
     * Note the rejection policy. The Spring default for a full queue is AbortPolicy, which
     * throws -- correct for a web request, wrong here, where "the pool is busy" should mean
     * "the caller waits a moment", not "the job is lost". CallerRunsPolicy makes the
     * submitting thread run the task itself, which also applies natural backpressure.
     */
    @Bean("jobExecutor")
    public Executor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let an in-flight job finish when the app is asked to stop, rather than being killed
        // mid-transaction. 60s is a guess -- it should exceed your typical run.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}