package com.example.catchme.config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정
 *
 * ✔ JWT 기반 인증
 * ✔ Stateless (세션 사용 안 함)
 * ✔ API 서버 전용 (HTML / Form Login 없음)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenProvider tokenProvider;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Security Filter Chain 설정
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // JWT 인증 필터 (UsernamePasswordAuthenticationFilter 이전에 실행)
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(tokenProvider);

        http
                /* =================================================
                   기본 보안 설정 비활성화
                   ================================================= */
                .csrf(csrf -> csrf.disable())          // JWT 사용 → CSRF 불필요
                .formLogin(form -> form.disable())     // Form Login 미사용
                .httpBasic(basic -> basic.disable())   // HTTP Basic 미사용

                /* =================================================
                   세션 관리 정책
                   ================================================= */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 예외 처리 설정 (🔥 핵심)
                .exceptionHandling(exception ->
                        exception
                                .authenticationEntryPoint(authenticationEntryPoint) // 401
                                .accessDeniedHandler(accessDeniedHandler)             // 403
                )

                /* =================================================
                   요청별 접근 권한 설정
                   ================================================= */
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 허용 (로그인/회원가입 등)
                        .requestMatchers(
                                "/api/auth/**",
                                "/h2-console/**"
                        ).permitAll()

                        // 2. [환자/USER 전용]
                        // - QR 생성
                        // - 내 분석 이력 조회
                        // - 분석 요청 (데이터 업로드는 환자가 하니까)
                        .requestMatchers(
                                "/api/link/qr",
                                "/api/predictions/history/me",
                                "/api/predictions/latest",
                                "/api/surveys",
                                "/api/surveys/history/me",
                                "/api/raw-data"
                        ).hasRole("USER")
                        // 3. [보호자/GUARDIAN 전용]
                        // - 환자 연결하기
                        // - 연결된 환자의 이력 조회
                        .requestMatchers(
                                "/api/link/connect",
                                "/api/predictions/history/patient",
                                "/api/surveys/history/patient"
                        ).hasRole("GUARDIAN")

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                /* =================================================
                   JWT 필터 등록
                   ================================================= */
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        /* =====================================================
           H2 콘솔 사용을 위한 설정 (개발용) , 운영환경에서는 제거하기
           ===================================================== */
        http.headers(headers ->
                headers.frameOptions(frame -> frame.disable())
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
