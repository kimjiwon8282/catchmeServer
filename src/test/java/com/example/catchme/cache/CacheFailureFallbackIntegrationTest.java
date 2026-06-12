package com.example.catchme.cache;

import com.example.catchme.config.externalApi.AiPredictionClient;
import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.model.Member;
import com.example.catchme.model.Role;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.config.auth.TokenProvider;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CacheFailureFallbackIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TokenProvider tokenProvider;

    @Autowired
    FailingCacheManager failingCacheManager;

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
        failingCacheManager.reset();
        memberRepository.deleteAll();
    }

    @Test
    void authUsesDbFallbackWhenCacheGetFailsAndKeepsDbRole() throws Exception {
        Member member = saveMember("cache-get-auth@catchme.com", Role.USER);
        String tokenWithWrongJwtRole = tokenProvider.generateToken(
                member.getId(),
                member.getEmail(),
                Role.GUARDIAN,
                Duration.ofHours(1)
        );
        failingCacheManager.cache("memberAuthCache").failGet();

        mockMvc.perform(post("/api/link/connect")
                        .header("Authorization", bearer(tokenWithWrongJwtRole))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkToken\":\"any\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(tokenWithWrongJwtRole)))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawnMemberIsRejectedWhenCacheGetFails() throws Exception {
        Member member = saveMember("withdrawn-cache-get@catchme.com", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));
        member.withdraw();
        memberRepository.saveAndFlush(member);
        failingCacheManager.cache("memberAuthCache").failGet();

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingMemberIsRejectedWhenCacheGetFails() throws Exception {
        String token = tokenProvider.generateToken(999999L, "missing-cache-get@catchme.com", Role.USER, Duration.ofHours(1));
        failingCacheManager.cache("memberAuthCache").failGet();

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authStillSucceedsWhenCachePutFails() throws Exception {
        Member member = saveMember("cache-put-auth@catchme.com", Role.USER);
        String token = tokenProvider.generateToken(member, Duration.ofHours(1));
        failingCacheManager.cache("memberAuthCache").failPut();

        mockMvc.perform(get("/api/test/ping").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void hospitalLookupCallsKakaoWhenCacheGetFails() throws Exception {
        String token = tokenProvider.generateToken(saveMember("hospital-get@catchme.com", Role.USER), Duration.ofHours(1));
        givenKakaoHospital();
        failingCacheManager.cache("hospitals").failGet();

        mockMvc.perform(get("/api/hospitals/nearby")
                        .header("Authorization", bearer(token))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Seoul Hospital"));

        verify(kakaoApiClient).searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyString());
    }

    @Test
    void hospitalLookupReturnsKakaoResultWhenCachePutFails() throws Exception {
        String token = tokenProvider.generateToken(saveMember("hospital-put@catchme.com", Role.USER), Duration.ofHours(1));
        givenKakaoHospital();
        failingCacheManager.cache("hospitals").failPut();

        mockMvc.perform(get("/api/hospitals/nearby")
                        .header("Authorization", bearer(token))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Seoul Hospital"));
    }

    @Test
    void hospitalLookupKeepsExistingKakaoFallbackWhenKakaoFails() throws Exception {
        String token = tokenProvider.generateToken(saveMember("hospital-kakao-fail@catchme.com", Role.USER), Duration.ofHours(1));
        when(kakaoApiClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("Kakao unavailable"));

        mockMvc.perform(get("/api/hospitals/nearby")
                        .header("Authorization", bearer(token))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private void givenKakaoHospital() {
        when(kakaoApiClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyString()))
                .thenReturn(Map.of(
                        "documents",
                        List.of(Map.of(
                                "place_name", "Seoul Hospital",
                                "category_name", "Hospital",
                                "address_name", "Seoul",
                                "y", "37.5665",
                                "x", "126.9780",
                                "distance", "120"
                        ))
                ));
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class CacheFailureTestConfig {

        @Bean(name = "redisCacheManager")
        @Primary
        FailingCacheManager redisCacheManager() {
            return new FailingCacheManager();
        }
    }

    static class FailingCacheManager implements CacheManager {

        private final Map<String, FailingCache> caches = new ConcurrentHashMap<>();

        @Override
        public Cache getCache(String name) {
            return cache(name);
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }

        FailingCache cache(String name) {
            return caches.computeIfAbsent(name, FailingCache::new);
        }

        void reset() {
            caches.values().forEach(FailingCache::reset);
        }
    }

    static class FailingCache extends ConcurrentMapCache {

        private boolean failGet;
        private boolean failPut;

        FailingCache(String name) {
            super(name);
        }

        void failGet() {
            this.failGet = true;
        }

        void failPut() {
            this.failPut = true;
        }

        void reset() {
            this.failGet = false;
            this.failPut = false;
            clear();
        }

        @Override
        public ValueWrapper get(Object key) {
            if (failGet) {
                throw new IllegalStateException("Redis GET unavailable");
            }
            return super.get(key);
        }

        @Override
        public void put(Object key, Object value) {
            if (failPut) {
                throw new IllegalStateException("Redis PUT unavailable");
            }
            super.put(key, value);
        }
    }
}
