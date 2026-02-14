package com.example.catchme.service.impl.user;
import com.example.catchme.config.externalApi.KakaoApiClient;
import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.exception.exceptions.ExternalApiException;
import com.example.catchme.service.interfaces.user.HospitalService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalServiceImpl implements HospitalService {

    private final KakaoApiClient kakaoApiClient; // 인터페이스 주입

    @Override
    // name: yaml에서 설정한 인스턴스 이름 ("kakaoApi")
    // fallbackMethod: 에러 발생 시 대신 실행할 메서드 이름
    @CircuitBreaker(name = "kakaoApi", fallbackMethod = "fallbackNearbyHospitals")
    @Bulkhead(name = "kakaoApi", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackNearbyHospitals")
    // key: 위도/경도에 1000을 곱해서 반올림 -> 문자열로 조합, value: CacheConfig에서 만든 저장소 이름 ("hospitals")
    @Cacheable(value = "hospitals", key = "T(java.lang.Math).round(#lat * 1000) + '_' + T(java.lang.Math).round(#lng * 1000)",cacheManager = "redisCacheManager")
    public List<HospitalResponse> findNearbyHospitals(double lat, double lng) {
        // [중요] 캐시가 적중(Hit)하면, 이 메서드는 아예 실행되지 않습니다.
        log.info("🚀 [Service] 카카오 API 호출 시도 (lat: {}, lng: {})", lat, lng);
        try {
            Map<String, Object> response = kakaoApiClient.searchKeyword(
                    "신경과",
                    lng,
                    lat,
                    3000,
                    "distance"
            );

            // 1. Body Null 체크
            if (response == null || !response.containsKey("documents")) {
                return new ArrayList<>(); //수정: Jackson이 역직렬화할 수 있는 일반 ArrayList 반환
            }

            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");

            return documents.stream()
                    .map(doc -> new HospitalResponse(
                            String.valueOf(doc.get("place_name")),
                            String.valueOf(doc.get("category_name")),
                            String.valueOf(doc.get("address_name")),
                            Double.parseDouble(String.valueOf(doc.get("y"))),
                            Double.parseDouble(String.valueOf(doc.get("x"))),
                            Integer.parseInt(String.valueOf(doc.get("distance")))
                    ))
                    .collect(Collectors.toList()); // 수정: 명시적으로 변형 가능한 List 반환

        } catch (Exception e) {
            // RestClient는 에러 발생 시 HttpClientErrorException 등을 던집니다.
            log.error("⚠️ [Service] API 호출 실패! 에러를 던져서 서킷 브레이커에게 알립니다. 원인: {}", e.getMessage());
            throw new ExternalApiException("카카오 맵 API 호출 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 🔥 Fallback 메서드 (장애 발생 시 실행)
     * 규칙 1: 리턴 타입이 원본 메서드와 같아야 함.
     * 규칙 2: 파라미터가 원본과 같고, 마지막에 Throwable을 받아야 함.
     */
    public List<HospitalResponse> fallbackNearbyHospitals(double lat, double lng, Throwable t) {
        // [로그 4] 상황별 Fallback 로그 (차단됨 vs 그냥 에러남)
        if (t instanceof CallNotPermittedException) {
            log.error("[Circuit Breaker OPEN] 서킷이 열려있습니다! API 호출을 아예 차단하고 즉시 Fallback을 실행합니다.");
        } else {
            log.error("[Circuit Breaker Catch] API 호출 중 에러 감지! Fallback 실행. 원인: {}", t.getMessage());
        }
        // 장애 시 빈 리스트 반환 (클라이언트는 에러 화면 대신 '결과 없음'을 보게 됨)
        // 상황에 따라 "일시적 장애로 조회 불가" 같은 더미 데이터를 줄 수도 있음
        return Collections.emptyList();
    }
}