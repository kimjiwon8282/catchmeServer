package com.example.catchme.config.externalApi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Configuration
public class HttpInterfaceConfig {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    @Value("${ai.fastapi.url}")
    private String aiApiUrl;

    @Bean
    public KakaoApiClient kakaoApiClient() {
        // 1. RestClient 설정 (Base URL, Header, Timeout)
        RestClient restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + kakaoApiKey)
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(Duration.ofSeconds(5))))
                .build();// 통신 기계(RestClient) 조립: 기본 주소, 인증 헤더(API Key), 타임아웃 설정

        // 2. Proxy Factory를 통해 인터페이스 구현체 생성
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(KakaoApiClient.class);
    }

    @Bean
    public AiPredictionClient aiPredictionClient() {
        // AI 서버용 RestClient 생성
        RestClient restClient = RestClient.builder()
                .baseUrl(aiApiUrl) // 예: http://localhost:8000
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE) // JSON 헤더 기본 설정
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(10)) // AI 분석은 오래 걸릴 수 있으니 넉넉하게 10초
                        .withReadTimeout(Duration.ofSeconds(10))))
                .build();

        // 인터페이스 프록시 생성
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(AiPredictionClient.class);
    }
}