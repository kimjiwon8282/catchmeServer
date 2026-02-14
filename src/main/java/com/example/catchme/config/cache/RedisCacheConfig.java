package com.example.catchme.config.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * 인증(로그인) 정보 전용 Redis 캐시 매니저
     * - 빈 이름: "redisCacheManager"
     * - 용도: UserDetails 객체 캐싱
     */
    @Bean(name = "redisCacheManager") // ⭐️ 빈 이름을 명시해서 Caffeine과 구분
    @Primary // @Cacheable을 쓸 때 cacheManager를 명시 안 하면 기본으로 얘를 씀
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 1. 유효 시간: 60분 (로그인 세션 유지 시간과 맞춤)
                .entryTtl(Duration.ofMinutes(60))
                // 2. null 값 금지 (불필요한 데이터 방지)
                .disableCachingNullValues()
                // 3. Key 직렬화: String (Redis-cli에서 보기 편함)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 4. Value 직렬화: JSON (사람이 읽을 수 있는 형태)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("hospitals", config.entryTtl(Duration.ofDays(1)))
                .build();
    }
}
