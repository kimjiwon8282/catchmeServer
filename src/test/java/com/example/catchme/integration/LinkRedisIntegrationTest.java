package com.example.catchme.integration;

import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.impl.user.LinkServiceImpl;
import com.example.catchme.service.interfaces.user.LinkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QR Redis 통합 테스트
 *
 * 목적:
 * 1) QR 토큰이 실제 Redis에 저장되는지
 * 2) TTL(10분)이 실제로 적용되는지
 * 3) 보호자 연동 성공 후 Redis 토큰이 삭제되는지
 *
 * 특징:
 * - UserRepository는 mock 처리
 * - Redis는 실제 localhost:6379에 붙어서 검증
 * - "로직 + Redis 저장소"를 함께 검증하는 통합 테스트
 *
 * 실행 전 준비:
 * - docker-compose up -d redis
 * - 또는 로컬 Redis 서버가 localhost:6379 에 떠 있어야 함
 */
@SpringJUnitConfig
@ContextConfiguration(classes = LinkRedisIntegrationTest.RedisIntegrationTestConfig.class)
class LinkRedisIntegrationTest {

    private static final String QR_KEY_PREFIX = "QR:LINK:";

    @MockitoBean
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private LinkService linkService;

    @org.springframework.beans.factory.annotation.Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void clearRedisKeys() {
        Set<String> keys = redisTemplate.keys(QR_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("QR 생성 시 토큰이 Redis에 10분 TTL로 저장된다")
    void generateQrToken_shouldStoreTokenInRedisWithTtl() {
        Long userId = 1L;
        User patient = user(userId, "user@test.com", Role.USER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(patient));

        QrLinkTokenResponse response = linkService.generateQrToken(userId);

        String key = QR_KEY_PREFIX + response.getLinkToken();
        String savedValue = redisTemplate.opsForValue().get(key);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        assertThat(response.getLinkToken()).isNotBlank();
        assertThat(savedValue).isEqualTo(String.valueOf(userId));
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(Duration.ofMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("유효한 QR 토큰이면 보호자-환자 연동 후 Redis 토큰이 삭제된다")
    void connectByQr_shouldLinkUsersAndDeleteRedisToken() {
        Long guardianId = 10L;
        Long userId = 20L;
        String token = "integration-qr-token";
        String key = QR_KEY_PREFIX + token;

        User guardian = user(guardianId, "guardian@test.com", Role.GUARDIAN);
        User patient = user(userId, "patient@test.com", Role.USER);

        redisTemplate.opsForValue().set(key, String.valueOf(userId), Duration.ofMinutes(10));
        when(userRepository.findAllById(List.of(guardianId, userId)))
                .thenReturn(List.of(guardian, patient));

        linkService.connectByQr(guardianId, token);

        assertThat(guardian.getLinkedUser()).isEqualTo(patient);
        assertThat(patient.getLinkedUser()).isEqualTo(guardian);
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("만료되었거나 존재하지 않는 토큰이면 Redis 삭제 없이 예외가 발생한다")
    void connectByQr_shouldThrowException_whenTokenMissing() {
        Long guardianId = 10L;
        String missingToken = "missing-token";
        String key = QR_KEY_PREFIX + missingToken;

        assertThatThrownBy(() -> linkService.connectByQr(guardianId, missingToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않거나 만료된 QR 토큰입니다.");

        verify(userRepository, never()).findAllById(anyList());
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    private User user(Long id, String email, Role role) {
        User user = User.builder()
                .email(email)
                .password("encoded-password")
                .name("테스트유저")
                .role(role)
                .build();

        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Configuration
    static class RedisIntegrationTestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", 6379);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        LinkService linkService(UserRepository userRepository, StringRedisTemplate redisTemplate) {
            return new LinkServiceImpl(userRepository, redisTemplate);
        }
    }
}
