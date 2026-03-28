package com.example.catchme.service.impl.user;

import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.exception.exceptions.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospitalServiceImplTest {

    @Mock
    private KakaoApiClient kakaoApiClient;

    @InjectMocks
    private HospitalServiceImpl hospitalService;

    @Nested
    @DisplayName("findNearbyHospitals")
    class FindNearbyHospitals {

        @Test
        @DisplayName("카카오 API 응답의 documents를 HospitalResponse 리스트로 매핑한다")
        void findNearbyHospitalsSuccess() { //정상흐름 테스트
            double lat = 35.1379;
            double lng = 129.0586;

            Map<String, Object> first = Map.of(
                    "place_name", "부산 신경과 의원",
                    "category_name", "의료,건강 > 병원 > 신경과",
                    "address_name", "부산 동구 초량동 123-4",
                    "y", "35.1381",
                    "x", "129.0588",
                    "distance", "120"
            );
            Map<String, Object> second = Map.of(
                    "place_name", "좋은 신경과",
                    "category_name", "의료,건강 > 병원 > 신경과",
                    "address_name", "부산 동구 수정동 55-1",
                    "y", "35.1390",
                    "x", "129.0601",
                    "distance", "230"
            );

            when(kakaoApiClient.searchKeyword("신경과", lng, lat, 3000, "distance"))
                    .thenReturn(Map.of("documents", List.of(first, second)));

            List<HospitalResponse> result = hospitalService.findNearbyHospitals(lat, lng);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("부산 신경과 의원");
            assertThat(result.get(0).getCategory()).isEqualTo("의료,건강 > 병원 > 신경과");
            assertThat(result.get(0).getAddress()).isEqualTo("부산 동구 초량동 123-4");
            assertThat(result.get(0).getLatitude()).isEqualTo(35.1381);
            assertThat(result.get(0).getLongitude()).isEqualTo(129.0588);
            assertThat(result.get(0).getDistance()).isEqualTo(120);

            assertThat(result.get(1).getName()).isEqualTo("좋은 신경과");
            assertThat(result.get(1).getDistance()).isEqualTo(230);

            verify(kakaoApiClient).searchKeyword("신경과", lng, lat, 3000, "distance");
        }

        @Test
        @DisplayName("응답이 null이면 빈 리스트를 반환한다")
        void returnsEmptyListWhenResponseIsNull() { //외부 api응답 자체가 null인 경우
            double lat = 35.1379;
            double lng = 129.0586;

            when(kakaoApiClient.searchKeyword("신경과", lng, lat, 3000, "distance"))
                    .thenReturn(null);

            List<HospitalResponse> result = hospitalService.findNearbyHospitals(lat, lng);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("documents 키가 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenDocumentsMissing() {
            double lat = 35.1379;
            double lng = 129.0586;

            when(kakaoApiClient.searchKeyword("신경과", lng, lat, 3000, "distance"))
                    .thenReturn(Map.of("meta", Map.of("total_count", 0)));

            List<HospitalResponse> result = hospitalService.findNearbyHospitals(lat, lng);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("카카오 API 호출 중 예외가 발생하면 ExternalApiException으로 감싸서 던진다")
        void throwsExternalApiExceptionWhenKakaoApiFails() {
            double lat = 35.1379;
            double lng = 129.0586;

            when(kakaoApiClient.searchKeyword("신경과", lng, lat, 3000, "distance"))
                    .thenThrow(new IllegalStateException("kakao api failed"));

            assertThatThrownBy(() -> hospitalService.findNearbyHospitals(lat, lng))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessage("카카오 맵 API 호출 중 오류가 발생했습니다.")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("fallbackNearbyHospitals")
    class FallbackNearbyHospitals {

        @Test
        @DisplayName("fallback은 예외 종류와 상관없이 빈 리스트를 반환한다")
        void fallbackReturnsEmptyList() {
            List<HospitalResponse> result = hospitalService.fallbackNearbyHospitals(
                    35.1379,
                    129.0586,
                    new RuntimeException("temporary failure")
            );

            assertThat(result).isEmpty();
        }
    }
}
