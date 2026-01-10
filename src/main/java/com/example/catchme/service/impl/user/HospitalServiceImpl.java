package com.example.catchme.service.impl.user;
import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.exception.exceptions.ExternalApiException;
import com.example.catchme.service.interfaces.user.HospitalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HospitalServiceImpl implements HospitalService {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate; // Bean 주입 (Timeout 설정됨)

    @Override
    public List<HospitalResponse> findNearbyHospitals(double lat, double lng) {
        try {
            // [수정 후] URI 객체로 받음 (해결책!)
            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                    .queryParam("query", "신경과")
                    .queryParam("x", lng)
                    .queryParam("y", lat)
                    .queryParam("radius", 3000)
                    .queryParam("sort", "distance")
                    .encode() // 한글 인코딩 처리
                    .build()
                    .toUri(); // String이 아니라 URI 객체로 반환

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    uri, // 🔥 여기에 String 대신 uri 객체를 넣습니다.
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            // 1. Body Null 체크
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("documents")) {
                return List.of();
            }

            List<Map<String, Object>> documents = (List<Map<String, Object>>) body.get("documents");

            return documents.stream()
                    .map(doc -> new HospitalResponse(
                            String.valueOf(doc.get("place_name")),
                            String.valueOf(doc.get("category_name")),
                            String.valueOf(doc.get("address_name")),
                            // 2. 안전한 파싱 (String.valueOf 사용)
                            Double.parseDouble(String.valueOf(doc.get("y"))),
                            Double.parseDouble(String.valueOf(doc.get("x"))),
                            Integer.parseInt(String.valueOf(doc.get("distance")))
                    ))
                    .toList();

        } catch (RestClientException e) {
            throw new ExternalApiException("카카오 맵 API 호출 중 오류가 발생했습니다.", e);
        } catch (NumberFormatException e) {
            // 데이터 파싱 중 오류 발생 시 처리 (로그 남기기 등)
            throw new ExternalApiException("병원 데이터 처리 중 오류가 발생했습니다.", e);
        }
    }
}