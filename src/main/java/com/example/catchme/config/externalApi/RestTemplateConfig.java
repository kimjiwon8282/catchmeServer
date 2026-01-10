package com.example.catchme.config.externalApi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5)) // 연결 타임아웃 5초
                .readTimeout(Duration.ofSeconds(5))    // 읽기 타임아웃 5초
                .build();
    }
}