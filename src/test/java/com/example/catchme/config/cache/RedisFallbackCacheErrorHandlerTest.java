package com.example.catchme.config.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisFallbackCacheErrorHandlerTest {

    private final RedisFallbackCacheErrorHandler handler = new RedisFallbackCacheErrorHandler();
    private final ConcurrentMapCache cache = new ConcurrentMapCache("memberAuthCache");
    private final RuntimeException exception = new IllegalStateException("redis unavailable");

    @Test
    void getErrorDoesNotRethrow() {
        assertThatCode(() -> handler.handleCacheGetError(exception, cache, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void putErrorDoesNotRethrow() {
        assertThatCode(() -> handler.handleCachePutError(exception, cache, 1L, "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void evictErrorRethrows() {
        assertThatThrownBy(() -> handler.handleCacheEvictError(exception, cache, 1L))
                .isSameAs(exception);
    }

    @Test
    void clearErrorRethrows() {
        assertThatThrownBy(() -> handler.handleCacheClearError(exception, cache))
                .isSameAs(exception);
    }
}
