package com.example.catchme.config.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class RedisFallbackCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn(
                "Spring Cache GET failed. cache={}, key={}, exception={}, message={}, fallback=invoke_target_method",
                cacheName(cache),
                key,
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn(
                "Spring Cache PUT failed. cache={}, key={}, exception={}, message={}, fallback=return_target_result",
                cacheName(cache),
                key,
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error(
                "Spring Cache EVICT failed. cache={}, key={}, exception={}, message={}, fallback=none",
                cacheName(cache),
                key,
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        throw exception;
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error(
                "Spring Cache CLEAR failed. cache={}, key=ALL, exception={}, message={}, fallback=none",
                cacheName(cache),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        throw exception;
    }

    private String cacheName(Cache cache) {
        return cache != null ? cache.getName() : "unknown";
    }
}
