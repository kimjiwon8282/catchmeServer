package com.example.catchme.config.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // 메인 클래스에 있는거 지우고 여기로 옮겨도 됨
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 기본 알바생 5명
        executor.setMaxPoolSize(10);    // 바쁘면 최대 10명까지 채용
        executor.setQueueCapacity(100); // 대기열 100개
        executor.setThreadNamePrefix("Async-User-"); // 로그에 찍힐 이름
        executor.initialize();
        return executor;
    }
}
