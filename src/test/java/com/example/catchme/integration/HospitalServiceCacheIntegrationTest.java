package com.example.catchme.integration;

import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.service.impl.user.HospitalServiceImpl;
import com.example.catchme.service.interfaces.user.HospitalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 병원 조회에서
 * 1) 동일 좌표 버킷은 캐시 재사용
 * 2) 다른 좌표 버킷은 외부 API 재호출
 * 를 검증한다.
 *
 * 참고:
 * - 실제 운영 코드는 RedisCacheManager를 쓰지만,
 *   테스트는 캐시 동작 자체 검증에 집중하기 위해
 *   이름만 동일한 ConcurrentMapCacheManager("redisCacheManager")를 사용한다.
 */
@SpringJUnitConfig(classes = {
        HospitalServiceImpl.class,
        HospitalServiceCacheIntegrationTest.CacheTestConfig.class
})
class HospitalServiceCacheIntegrationTest {

    @Autowired
    private HospitalService hospitalService;

    @Autowired
    private KakaoApiClient kakaoApiClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(kakaoApiClient);
        cacheManager.getCache("hospitals").clear();
    }

    @Test
    @DisplayName("같은 좌표 버킷으로 두 번 조회하면 외부 API는 한 번만 호출된다")
    void findNearbyHospitals_shouldReuseCache_whenCoordinatesFallIntoSameBucket() {
        when(kakaoApiClient.searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance")))
                .thenReturn(kakaoResponse("서울 신경과"));

        List<HospitalResponse> first = hospitalService.findNearbyHospitals(37.56651, 126.97801);
        List<HospitalResponse> second = hospitalService.findNearbyHospitals(37.56654, 126.97804);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.get(0).getName()).isEqualTo("서울 신경과");
        assertThat(second.get(0).getName()).isEqualTo("서울 신경과");

        verify(kakaoApiClient, times(1))
                .searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance"));
    }

    @Test
    @DisplayName("좌표 버킷이 달라지면 외부 API를 다시 호출한다")
    void findNearbyHospitals_shouldCallApiAgain_whenCoordinatesFallIntoDifferentBucket() {
        when(kakaoApiClient.searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance")))
                .thenReturn(kakaoResponse("서울 신경과"));

        hospitalService.findNearbyHospitals(37.5665, 126.9780);
        hospitalService.findNearbyHospitals(37.5676, 126.9792);

        verify(kakaoApiClient, times(2))
                .searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance"));
    }

    @Test
    @DisplayName("외부 API 응답에 documents가 없으면 빈 리스트를 반환한다")
    void findNearbyHospitals_shouldReturnEmptyList_whenDocumentsMissing() {
        when(kakaoApiClient.searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance")))
                .thenReturn(Map.of());

        List<HospitalResponse> result = hospitalService.findNearbyHospitals(37.5665, 126.9780);

        assertThat(result).isEmpty();
        verify(kakaoApiClient, times(1))
                .searchKeyword(eq("신경과"), anyDouble(), anyDouble(), eq(3000), eq("distance"));
    }

    private Map<String, Object> kakaoResponse(String placeName) {
        return Map.of(
                "documents", List.of(
                        Map.of(
                                "place_name", placeName,
                                "category_name", "병원 > 신경과",
                                "address_name", "서울시 중구 테스트로 1",
                                "y", "37.5665",
                                "x", "126.9780",
                                "distance", "120"
                        )
                )
        );
    }

    @TestConfiguration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        HospitalService hospitalService(KakaoApiClient kakaoApiClient) {
            return new HospitalServiceImpl(kakaoApiClient);
        }

        @Bean
        KakaoApiClient kakaoApiClient() {
            return mock(KakaoApiClient.class);
        }

        @Bean(name = "redisCacheManager")
        CacheManager redisCacheManager() {
            return new ConcurrentMapCacheManager("hospitals");
        }
    }
}
