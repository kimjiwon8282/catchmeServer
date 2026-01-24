package com.example.catchme.config.cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching // 🔥 스프링의 캐시 기능을 켭니다.
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        // "hospitals"라는 이름의 캐시 저장소를 관리하는 매니저 생성
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("hospitals");

        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 1. 시간 제한: 쓰기(Write) 이후 1시간이 지나면 삭제 (데이터 최신화)
                .expireAfterWrite(1, TimeUnit.HOURS)
                // 2. 개수 제한: 최대 1,000개까지만 저장 (메모리 보호)
                .maximumSize(1000)
                // 3. 통계 기록: 나중에 캐시가 얼마나 잘 동작하는지 볼 때 사용
                .recordStats());

        return cacheManager;
    }
}
