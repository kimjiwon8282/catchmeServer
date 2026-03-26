package com.example.catchme.service.impl.user;

import com.example.catchme.dto.QrLinkTokenResponse;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LinkServiceImpl linkService;

    @Nested
    @DisplayName("generateQrToken")
    class GenerateQrToken {

        @Test
        @DisplayName("연동되지 않은 USER는 QR 토큰을 생성하고 Redis에 10분 TTL로 저장한다")
        void generateQrTokenSuccess() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com", Role.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            QrLinkTokenResponse response = linkService.generateQrToken(userId);

            assertThat(response).isNotNull();
            assertThat(response.getLinkToken()).isNotBlank();
            verify(userRepository).findById(userId);
            verify(redisTemplate).opsForValue();
            verify(valueOperations).set( //redis저장 규칙까지 검사
                    eq("QR:LINK:" + response.getLinkToken()),
                    eq(String.valueOf(userId)),
                    eq(Duration.ofMinutes(10))
            );
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 Redis에 저장하지 않는다")
        void generateQrTokenFailsWhenUserNotFound() {
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> linkService.generateQrToken(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(redisTemplate, never()).opsForValue();
            verify(valueOperations, never()).set(eq("QR:LINK:any"), eq("999"), eq(Duration.ofMinutes(10)));
        }

        @Test
        @DisplayName("이미 보호자와 연동된 계정이면 QR 토큰을 생성하지 않는다")
        void generateQrTokenFailsWhenUserAlreadyLinked() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com", Role.USER);
            User guardian = user(2L, "guardian@catchme.com", Role.GUARDIAN);
            user.setLinkedUser(guardian);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> linkService.generateQrToken(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 보호자와 연동된 계정입니다.");

            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("connectByQr")
    class ConnectByQr {

        @Test
        @DisplayName("유효한 QR 토큰이면 보호자와 환자를 1:1로 연동하고 Redis 토큰을 삭제한다")
        void connectByQrSuccess() {
            Long guardianId = 10L;
            Long userId = 20L;
            String linkToken = "qr-token";
            String key = "QR:LINK:" + linkToken;

            User guardian = user(guardianId, "guardian@catchme.com", Role.GUARDIAN);
            User user = user(userId, "user@catchme.com", Role.USER);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(key)).thenReturn(String.valueOf(userId));
            when(userRepository.findAllById(List.of(guardianId, userId))).thenReturn(List.of(guardian, user));

            linkService.connectByQr(guardianId, linkToken);

            assertThat(guardian.getLinkedUser()).isEqualTo(user);
            assertThat(user.getLinkedUser()).isEqualTo(guardian);
            verify(redisTemplate).opsForValue();
            verify(valueOperations).get(key);
            verify(userRepository).findAllById(List.of(guardianId, userId));
            verify(redisTemplate).delete(key);
        }

        @Test
        @DisplayName("QR 토큰이 없거나 만료되면 IllegalArgumentException을 던지고 연동하지 않는다")
        void connectByQrFailsWhenTokenMissingOrExpired() {
            Long guardianId = 10L;
            String linkToken = "expired-token";
            String key = "QR:LINK:" + linkToken;

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(key)).thenReturn(null);

            assertThatThrownBy(() -> linkService.connectByQr(guardianId, linkToken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유효하지 않거나 만료된 QR 토큰입니다.");

            verify(userRepository, never()).findAllById(anyList());
            verify(redisTemplate, never()).delete(key);
        }

        @Test
        @DisplayName("환자 또는 보호자 정보가 부족하면 UserNotFoundException을 던진다")
        void connectByQrFailsWhenUsersMissing() {
            Long guardianId = 10L;
            Long userId = 20L;
            String linkToken = "qr-token";
            String key = "QR:LINK:" + linkToken;
            User guardian = user(guardianId, "guardian@catchme.com", Role.GUARDIAN);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(key)).thenReturn(String.valueOf(userId));
            when(userRepository.findAllById(List.of(guardianId, userId))).thenReturn(List.of(guardian));

            assertThatThrownBy(() -> linkService.connectByQr(guardianId, linkToken))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("환자 또는 보호자 정보를 찾을 수 없습니다.");

            verify(redisTemplate, never()).delete(key); //토큰이 삭제되면 안된다.
        }

        @Test
        @DisplayName("QR 토큰 대상의 role이 USER가 아니면 연동하지 않는다")
        void connectByQrFailsWhenQrTargetIsNotUser() {
            Long guardianId = 10L;
            Long targetId = 20L;
            String linkToken = "qr-token";
            String key = "QR:LINK:" + linkToken;

            User guardian = user(guardianId, "guardian@catchme.com", Role.GUARDIAN);
            User notPatient = user(targetId, "other-guardian@catchme.com", Role.GUARDIAN);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(key)).thenReturn(String.valueOf(targetId));
            when(userRepository.findAllById(List.of(guardianId, targetId))).thenReturn(List.of(guardian, notPatient));

            assertThatThrownBy(() -> linkService.connectByQr(guardianId, linkToken))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("QR 대상이 환자가 아닙니다.");

            assertThat(guardian.getLinkedUser()).isNull();
            assertThat(notPatient.getLinkedUser()).isNull();
            verify(redisTemplate, never()).delete(key);
        }

        @Test
        @DisplayName("이미 연동된 계정이 있으면 중복 연동을 막고 토큰을 삭제하지 않는다")
        void connectByQrFailsWhenAccountAlreadyLinked() {
            Long guardianId = 10L;
            Long userId = 20L;
            String linkToken = "qr-token";
            String key = "QR:LINK:" + linkToken;

            User guardian = user(guardianId, "guardian@catchme.com", Role.GUARDIAN);
            User user = user(userId, "user@catchme.com", Role.USER);
            User anotherUser = user(30L, "another@catchme.com", Role.USER);
            guardian.setLinkedUser(anotherUser);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(key)).thenReturn(String.valueOf(userId));
            when(userRepository.findAllById(List.of(guardianId, userId))).thenReturn(List.of(guardian, user));

            assertThatThrownBy(() -> linkService.connectByQr(guardianId, linkToken))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 연동된 계정이 존재합니다.");

            assertThat(user.getLinkedUser()).isNull();
            verify(redisTemplate, never()).delete(key);
        }
    }

    private User user(Long id, String email, Role role) {
        User user = User.builder()
                .email(email)
                .password("encodedPassword")
                .name("테스트유저")
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
