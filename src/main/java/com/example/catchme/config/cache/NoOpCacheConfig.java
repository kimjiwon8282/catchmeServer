package com.example.catchme.config.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableCaching
@Profile("perf-nocache")
public class NoOpCacheConfig {

    @Bean(name = "redisCacheManager")
    @Primary
    public CacheManager redisCacheManager() {
        return new NoOpCacheManager();
    }
}
