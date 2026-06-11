package com.example.catchme.auth;

import com.example.catchme.config.auth.MemberPrincipal;
import com.example.catchme.config.auth.JwtProperties;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.config.externalApi.AiPredictionClient;
import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.dto.SignupRequest;
import com.example.catchme.model.Member;
import com.example.catchme.model.Role;
import com.example.catchme.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String AUTH_CACHE = "memberAuthCache";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TokenProvider tokenProvider;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    StringRedisTemplate redisTemplate;

    @MockBean
    SqsTemplate sqsTemplate;

    @MockBean
    S3Client s3Client;

    @MockBean
    KakaoApiClient kakaoApiClient;

    @MockBean
    AiPredictionClient aiPredictionClient;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(redisTemplate.keys(AUTH_CACHE + "::*"));
        memberRepository.deleteAll();
    }

    @Test
    void signupAndLoginUseMemberTableAndReturnAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newSignup("signup@catchme.com", "pw1234", "tester", Role.USER))))
                .andExpect(status().isCreated());

        Member saved = memberRepository.findByEmail("signup@catchme.com").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("signup@catchme.com");
        assertThat(saved.getPassword()).isNotEqualTo("pw1234");
        assertThat(saved.getRole()).isEqualTo(Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("signup@catchme.com", "pw1234", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginFailsForWrongPasswordMissingEmailAndWithdrawnMember() throws Exception {
        saveMember("login@catchme.com", "pw1234", Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("login@catchme.com", "bad", null))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("missing@catchme.com", "pw1234", null))))
                .andExpect(status().isUnauthorized());

        Member withdrawn = saveMember("withdrawn@catchme.com", "pw1234", Role.USER);
        withdrawn.withdraw();
        memberRepository.saveAndFlush(withdrawn);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("withdrawn@catchme.com", "pw1234", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtAuthenticationAcceptsValidTokenAndRejectsExpiredInvalidMissingAndWithdrawnMembers() throws Exception {
        Member member = saveMember("jwt@catchme.com", "pw1234", Role.USER);
        String validToken = tokenProvider.generateToken(member, Duration.ofHours(1));

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(validToken)))
                .andExpect(status().isOk());

        String expiredToken = tokenProvider.generateToken(member, Duration.ofSeconds(-1));
        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(validToken + "x")))
                .andExpect(status().isUnauthorized());

        String missingMemberToken = tokenProvider.generateToken(999999L, "missing@catchme.com", Role.USER, Duration.ofHours(1));
        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(missingMemberToken)))
                .andExpect(status().isUnauthorized());

        String missingIdClaimToken = generateTokenWithoutIdClaim(member.getEmail(), Role.USER, Duration.ofHours(1));
        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(missingIdClaimToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());

        member.withdraw();
        memberRepository.saveAndFlush(member);
        redisTemplate.delete(cacheKey(member.getId()));

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(validToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationPrincipalIsMemberPrincipalWithoutSensitiveFields() {
        Member member = saveMember("principal@catchme.com", "pw1234", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));

        Authentication authentication = tokenProvider.getAuthentication(token);

        assertThat(authentication.getPrincipal()).isInstanceOf(MemberPrincipal.class);
        assertThat(authentication.getPrincipal()).isNotInstanceOf(Member.class);
        assertThat(MemberPrincipal.class.getDeclaredFields())
                .extracting("name")
                .containsExactlyInAnyOrder("memberId", "email", "role", "enabled");
        assertThat(MemberPrincipal.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("password", "fcmToken", "linkedMember");
    }

    @Test
    void jwtRequestCreatesMemberAuthCacheWithDtoShapeAndTtlThenAuthenticatesFromCache() throws Exception {
        Member member = saveMember("cache@catchme.com", "pw1234", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));

        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isFalse();

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        String cached = redisTemplate.opsForValue().get(cacheKey(member.getId()));
        assertThat(cached).contains("MemberAuthCacheDto");
        assertThat(cached).contains("memberId", "email", "role", "enabled");
        assertThat(cached).contains("cache@catchme.com", "USER");
        assertThat(cached).doesNotContain("password", "fcmToken", "linkedMember", "withdrawnAt",
                "hibernateLazyInitializer", "handler", "SurveyResult", "RawDataFile");

        Long ttlMinutes = redisTemplate.getExpire(cacheKey(member.getId()), TimeUnit.MINUTES);
        assertThat(ttlMinutes).isNotNull();
        assertThat(ttlMinutes).isBetween(0L, 30L);

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void serverSideRoleFromCacheOverridesJwtAuthClaimAndRoleEndpointsReturnForbidden() throws Exception {
        Member user = saveMember("role-user@catchme.com", "pw1234", Role.USER);
        Member guardian = saveMember("role-guardian@catchme.com", "pw1234", Role.GUARDIAN);

        String tokenWithWrongJwtRole = tokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                Role.GUARDIAN,
                Duration.ofHours(1)
        );

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(tokenWithWrongJwtRole))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("linkToken", "any"))))
                .andExpect(status().isForbidden());

        String guardianToken = tokenProvider.generateToken(guardian, Duration.ofHours(1));
        mockMvc.perform(post("/api/link/qr").header("Authorization", bearer(guardianToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawalEvictsAuthCacheAndSameTokenIsRejectedAfterWithdrawal() throws Exception {
        Member member = saveMember("delete@catchme.com", "pw1234", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isTrue();

        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isFalse();
        assertThat(memberRepository.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeEvictsButNameAndFcmTokenChangesDoNotEvictAuthCache() throws Exception {
        Member member = saveMember("change@catchme.com", "pw1234", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isTrue();

        NameUpdateRequest nameRequest = new NameUpdateRequest();
        setField(nameRequest, "name", "new-name");
        mockMvc.perform(patch("/api/users/name")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(nameRequest)))
                .andExpect(status().isNoContent());
        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("change@catchme.com", "pw1234", "fcm-token"))))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isTrue();

        PasswordUpdateRequest passwordRequest = new PasswordUpdateRequest();
        setField(passwordRequest, "currentPassword", "pw1234");
        setField(passwordRequest, "newPassword", "new-pw1234");
        mockMvc.perform(patch("/api/users/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(passwordRequest)))
                .andExpect(status().isNoContent());
        assertThat(redisTemplate.hasKey(cacheKey(member.getId()))).isFalse();
    }

    private Member saveMember(String email, String rawPassword, Role role) {
        Member member = Member.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .name("tester")
                .role(role)
                .build();
        return memberRepository.saveAndFlush(member);
    }

    private SignupRequest newSignup(String email, String password, String name, Role role) throws Exception {
        SignupRequest request = new SignupRequest();
        setField(request, "email", email);
        setField(request, "password", password);
        setField(request, "name", name);
        setField(request, "role", role);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String cacheKey(Long memberId) {
        return AUTH_CACHE + "::" + memberId;
    }

    private String generateTokenWithoutIdClaim(String email, Role role, Duration duration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + duration.toMillis());

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setSubject(email)
                .claim("auth", "ROLE_" + role.name())
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8)
        );
    }
}
