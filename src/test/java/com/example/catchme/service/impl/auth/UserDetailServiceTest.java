package com.example.catchme.config.auth;

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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailService userDetailService;

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("활성 사용자 이메일이 존재하면 UserDetails를 반환한다")
        void loadUserByUsernameSuccess() {
            String email = "user@catchme.com";
            User user = User.builder()
                    .email(email)
                    .password("encoded-password")
                    .name("지원")
                    .role(Role.USER)
                    .build();

            when(userRepository.findByEmailAndWithdrawnFalse(email)).thenReturn(Optional.of(user));

            UserDetails result = userDetailService.loadUserByUsername(email);

            assertThat(result).isSameAs(user);
            assertThat(result.getUsername()).isEqualTo(email);
            assertThat(result.getPassword()).isEqualTo("encoded-password");
            assertThat(result.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_USER");

            verify(userRepository).findByEmailAndWithdrawnFalse(email);
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 UsernameNotFoundException을 던진다")
        void throwsWhenUserNotFound() {
            String email = "missing@catchme.com";

            when(userRepository.findByEmailAndWithdrawnFalse(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found: " + email);

            verify(userRepository).findByEmailAndWithdrawnFalse(email);
        }

        @Test
        @DisplayName("탈퇴한 사용자는 조회 대상에서 제외되어 UsernameNotFoundException을 던진다")
        void throwsWhenUserIsWithdrawn() {
            String email = "withdrawn@catchme.com";

            when(userRepository.findByEmailAndWithdrawnFalse(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found: " + email);

            verify(userRepository).findByEmailAndWithdrawnFalse(email);
            verify(userRepository, never()).findByEmail(email);
        }
    }
}
