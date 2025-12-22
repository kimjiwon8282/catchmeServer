package com.example.catchme.config.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 *
 * ✔ 모든 HTTP 요청마다 한 번 실행됨 (OncePerRequestFilter)
 * ✔ Authorization 헤더에서 JWT 추출
 * ✔ 토큰 검증 → Authentication 생성 → SecurityContext 저장
 *
 * 이 필터는 "로그인 처리"가 아니라
 * "이미 발급된 JWT를 검증하는 역할"만 담당한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;

    /**
     * 실제 필터 로직
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1️⃣ HTTP 요청 헤더에서 JWT 추출
        String token = resolveToken(request);

        // 2️⃣ 토큰이 존재하고 + 유효하다면 인증 처리
        if (token != null && tokenProvider.validateToken(token)) {

            // 3️⃣ JWT → Authentication 객체 생성
            Authentication authentication =
                    tokenProvider.getAuthentication(token);

            // 4️⃣ SecurityContext에 인증 정보 저장
            // → 이 시점 이후로 Spring Security는
            //   "인증된 사용자"로 요청을 처리함
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        // 5️⃣ 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 "Bearer {token}" 형식의 JWT 추출
     *
     * @param request HttpServletRequest
     * @return JWT 문자열 또는 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // "Bearer "로 시작하는 경우만 토큰으로 인정
        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {

            return bearerToken.substring(7); // "Bearer " 이후 토큰만 추출
        }

        return null;
    }
}