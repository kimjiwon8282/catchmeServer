package com.example.catchme.link;

import com.example.catchme.config.externalApi.AiPredictionClient;
import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.model.Member;
import com.example.catchme.model.Role;
import com.example.catchme.repository.MemberRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkRedisFailureIntegrationTest {

    private static final String QR_UNAVAILABLE_MESSAGE = "QR 연동 기능을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";

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
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TokenProvider tokenProvider;

    @Autowired
    EntityManager entityManager;

    @MockBean
    StringRedisTemplate redisTemplate;

    @MockBean
    SqsTemplate sqsTemplate;

    @MockBean
    S3Client s3Client;

    @MockBean
    KakaoApiClient kakaoApiClient;

    @MockBean
    AiPredictionClient aiPredictionClient;

    ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void qrCreateReturnsServiceUnavailableWhenRedisSetFails() throws Exception {
        Member user = saveMember("qr-set-user@catchme.com", Role.USER);
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        mockMvc.perform(post("/api/link/qr")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(QR_UNAVAILABLE_MESSAGE))
                .andExpect(jsonPath("$.linkToken").doesNotExist());
    }

    @Test
    void qrConnectReturnsServiceUnavailableWhenRedisGetFailsWithoutChangingDb() throws Exception {
        Member user = saveMember("qr-get-user@catchme.com", Role.USER);
        Member guardian = saveMember("qr-get-guardian@catchme.com", Role.GUARDIAN);
        when(valueOperations.get("QR:LINK:any-token"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"any-token\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(QR_UNAVAILABLE_MESSAGE));

        entityManager.clear();
        assertThat(memberRepository.findById(guardian.getId()).orElseThrow().getLinkedMember()).isNull();
        assertThat(memberRepository.findById(user.getId()).orElseThrow().getLinkedMember()).isNull();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void qrConnectReturnsServiceUnavailableWhenRedisDeleteFailsAndRollsBackDbLink() throws Exception {
        Member user = saveMember("qr-delete-user@catchme.com", Role.USER);
        Member guardian = saveMember("qr-delete-guardian@catchme.com", Role.GUARDIAN);
        when(valueOperations.get("QR:LINK:delete-token")).thenReturn(String.valueOf(user.getId()));
        when(redisTemplate.delete("QR:LINK:delete-token"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"delete-token\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(QR_UNAVAILABLE_MESSAGE));

        entityManager.clear();
        assertThat(memberRepository.findById(guardian.getId()).orElseThrow().getLinkedMember()).isNull();
        assertThat(memberRepository.findById(user.getId()).orElseThrow().getLinkedMember()).isNull();
    }

    @Test
    void qrCreateStoresTokenWithTenMinuteTtlWhenRedisIsAvailable() throws Exception {
        Member user = saveMember("qr-normal-user@catchme.com", Role.USER);

        mockMvc.perform(post("/api/link/qr")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkToken").isNotEmpty());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
        assertThat(keyCaptor.getValue()).startsWith("QR:LINK:");
    }

    @Test
    void qrConnectLinksMembersAndDeletesTokenWhenRedisIsAvailable() throws Exception {
        Member user = saveMember("qr-connect-user@catchme.com", Role.USER);
        Member guardian = saveMember("qr-connect-guardian@catchme.com", Role.GUARDIAN);
        when(valueOperations.get("QR:LINK:ok-token")).thenReturn(String.valueOf(user.getId()));
        when(redisTemplate.delete("QR:LINK:ok-token")).thenReturn(true);

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"ok-token\"}"))
                .andExpect(status().isOk());

        entityManager.clear();
        assertThat(memberRepository.findById(guardian.getId()).orElseThrow().getLinkedMember()).isNotNull();
        assertThat(memberRepository.findById(user.getId()).orElseThrow().getLinkedMember()).isNotNull();
        verify(redisTemplate).delete("QR:LINK:ok-token");
    }

    @Test
    void missingQrTokenKeepsExistingBadRequestResponse() throws Exception {
        Member guardian = saveMember("qr-missing-guardian@catchme.com", Role.GUARDIAN);
        when(valueOperations.get("QR:LINK:missing-token")).thenReturn(null);

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"missing-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alreadyLinkedMemberKeepsExistingConflictResponse() throws Exception {
        Member existingUser = saveMember("existing-user@catchme.com", Role.USER);
        Member existingGuardian = saveMember("existing-guardian@catchme.com", Role.GUARDIAN);
        existingUser.setLinkedMember(existingGuardian);
        existingGuardian.setLinkedMember(existingUser);
        Member newGuardian = saveMember("new-guardian@catchme.com", Role.GUARDIAN);
        memberRepository.saveAndFlush(existingUser);
        memberRepository.saveAndFlush(existingGuardian);
        when(valueOperations.get("QR:LINK:linked-token")).thenReturn(String.valueOf(existingUser.getId()));

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(newGuardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"linked-token\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void roleGuardsForQrApisStayUnchanged() throws Exception {
        Member user = saveMember("qr-role-user@catchme.com", Role.USER);
        Member guardian = saveMember("qr-role-guardian@catchme.com", Role.GUARDIAN);

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"any\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/link/qr")
                        .header("Authorization", bearer(guardian)))
                .andExpect(status().isForbidden());
    }

    private Member saveMember(String email, Role role) {
        Member member = Member.builder()
                .email(email)
                .password(passwordEncoder.encode("pw1234"))
                .name("tester")
                .role(role)
                .build();
        return memberRepository.saveAndFlush(member);
    }

    private String bearer(Member member) {
        return "Bearer " + tokenProvider.generateToken(member, Duration.ofHours(1));
    }
}
