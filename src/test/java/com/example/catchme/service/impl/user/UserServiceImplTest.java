package com.example.catchme.service.impl.user;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.exception.exceptions.InvalidPasswordException;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache userCache;

    @InjectMocks
    private UserServiceImpl userService; //JPA dirtyChecking을 전제로 하는 구조.

    @Nested
    @DisplayName("updateName")
    class UpdateName {

        @Test
        @DisplayName("사용자 이름을 변경하고 userCache를 비운다")
        void updateNameSuccess() { //도메인 상태 변경 + 캐시 무효화
            Long userId = 1L;
            User user = user("user@catchme.com", "encodedPassword", "기존이름", Role.USER);
            NameUpdateRequest request = nameUpdateRequest("새이름");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(cacheManager.getCache("userCache")).thenReturn(userCache); //userCache도 정상적으로 가져온다는 세팅

            userService.updateName(userId, request);

            assertThat(user.getName()).isEqualTo("새이름");
            verify(userRepository).findById(userId);
            verify(cacheManager).getCache("userCache");
            verify(userCache).evict("user@catchme.com");
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 캐시에 접근하지 않는다")
        void updateNameFailsWhenUserNotFound() {
            Long userId = 999L;
            NameUpdateRequest request = nameUpdateRequest("새이름");
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateName(userId, request))
                    .isInstanceOf(UserNotFoundException.class)//사용자가 없으면 Exception을 던진다
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(cacheManager, never()).getCache(anyString());
            verify(userCache, never()).evict(anyString());
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("현재 비밀번호가 일치하면 새 비밀번호로 변경하고 userCache를 비운다")
        void updatePasswordSuccess() {
            Long userId = 1L;
            User user = user("user@catchme.com", "encodedOldPassword", "지원", Role.USER);
            PasswordUpdateRequest request = passwordUpdateRequest("plainOldPassword", "plainNewPassword");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("plainOldPassword", "encodedOldPassword")).thenReturn(true);
            when(passwordEncoder.encode("plainNewPassword")).thenReturn("encodedNewPassword");
            when(cacheManager.getCache("userCache")).thenReturn(userCache);

            userService.updatePassword(userId, request);

            assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
            verify(passwordEncoder).matches("plainOldPassword", "encodedOldPassword");
            verify(passwordEncoder).encode("plainNewPassword");
            verify(cacheManager).getCache("userCache");
            verify(userCache).evict("user@catchme.com");
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 InvalidPasswordException을 던지고 변경하지 않는다")
        void updatePasswordFailsWhenCurrentPasswordDoesNotMatch() {
            Long userId = 1L;
            User user = user("user@catchme.com", "encodedOldPassword", "지원", Role.USER);
            PasswordUpdateRequest request = passwordUpdateRequest("wrongPassword", "plainNewPassword");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", "encodedOldPassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.updatePassword(userId, request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessage("비밀번호가 올바르지 않습니다.");

            assertThat(user.getPassword()).isEqualTo("encodedOldPassword");
            verify(passwordEncoder).matches("wrongPassword", "encodedOldPassword");
            verify(passwordEncoder, never()).encode(anyString());
            verify(cacheManager, never()).getCache(anyString());
            verify(userCache, never()).evict(anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던진다")
        void updatePasswordFailsWhenUserNotFound() {
            Long userId = 999L;
            PasswordUpdateRequest request = passwordUpdateRequest("plainOldPassword", "plainNewPassword");
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updatePassword(userId, request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(passwordEncoder, never()).encode(anyString());
            verify(cacheManager, never()).getCache(anyString());
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("회원 탈퇴 시 withdrawn 처리하고 FCM 토큰을 제거한 뒤 userCache를 비운다")
        void deleteUserSuccess() {
            Long userId = 1L;
            User user = user("user@catchme.com", "encodedPassword", "지원", Role.USER);
            user.updateFcmToken("fcm-token-123");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(cacheManager.getCache("userCache")).thenReturn(userCache);

            userService.deleteUser(userId);

            assertThat(user.isWithdrawn()).isTrue();
            assertThat(user.isEnabled()).isFalse();
            assertThat(user.getWithdrawnAt()).isNotNull();
            assertThat(user.getFcmToken()).isNull();
            verify(cacheManager).getCache("userCache");
            verify(userCache).evict("user@catchme.com");
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 캐시에 접근하지 않는다")
        void deleteUserFailsWhenUserNotFound() {
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(cacheManager, never()).getCache(anyString());
            verify(userCache, never()).evict(anyString());
        }
    }

    private NameUpdateRequest nameUpdateRequest(String name) {
        NameUpdateRequest request = new NameUpdateRequest();
        ReflectionTestUtils.setField(request, "name", name);
        return request;
    }

    private PasswordUpdateRequest passwordUpdateRequest(String currentPassword, String newPassword) {
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        ReflectionTestUtils.setField(request, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(request, "newPassword", newPassword);
        return request;
    }

    private User user(String email, String password, String name, Role role) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .build();
    }
}
