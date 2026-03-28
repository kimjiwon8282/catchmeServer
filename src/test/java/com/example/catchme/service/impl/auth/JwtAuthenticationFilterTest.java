package com.example.catchme.config.auth;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("Bearer 토큰이 유효하면 Authentication을 SecurityContext에 저장하고 다음 필터로 넘긴다")
        void setsAuthenticationWhenBearerTokenIsValid() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer valid-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "user@catchme.com",
                    "valid-token",
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

            when(tokenProvider.validateToken("valid-token")).thenReturn(true);
            when(tokenProvider.getAuthentication("valid-token")).thenReturn(authentication);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Authentication actual = SecurityContextHolder.getContext().getAuthentication();
            assertThat(actual).isSameAs(authentication);
            assertThat(actual.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_USER");

            verify(tokenProvider).validateToken("valid-token");
            verify(tokenProvider).getAuthentication("valid-token");
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 인증 처리 없이 다음 필터로 넘긴다")
        void skipsAuthenticationWhenAuthorizationHeaderMissing() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
            verify(tokenProvider, never()).getAuthentication(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("Bearer 형식이 아니면 인증 처리 없이 다음 필터로 넘긴다")
        void skipsAuthenticationWhenHeaderIsNotBearerFormat() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic abcdefg");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
            verify(tokenProvider, never()).getAuthentication(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("Bearer 토큰이 있어도 유효하지 않으면 SecurityContext를 비운 채 다음 필터로 넘긴다")
        void skipsAuthenticationWhenBearerTokenIsInvalid() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer invalid-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            when(tokenProvider.validateToken("invalid-token")).thenReturn(false);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(tokenProvider).validateToken("invalid-token");
            verify(tokenProvider, never()).getAuthentication(org.mockito.ArgumentMatchers.anyString());
        }
    }
}
