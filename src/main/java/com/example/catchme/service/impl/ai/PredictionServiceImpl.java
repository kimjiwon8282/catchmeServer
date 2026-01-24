package com.example.catchme.service.impl.ai;

import com.example.catchme.config.externalApi.AiPredictionClient;
import com.example.catchme.dto.AiPredictionRequest;
import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.exception.exceptions.ExternalApiException;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.interfaces.ai.PredictionService;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionServiceImpl implements PredictionService {

    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionClient aiPredictionClient;
    private final PredictionResultService predictionResultService;

    @Override
    @CircuitBreaker(name = "aiPrediction", fallbackMethod = "fallbackPrediction")
    @Bulkhead(name = "aiPrediction", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackPrediction")
    public AiPredictionResponse requestLatestPrediction(User user) {
        //조회
        RawDataFile rawDataFile = rawDataFileRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalStateException("측정 데이터가 없습니다."));

        if (rawDataFile.isAnalyzed()) {
            throw new IllegalStateException("이미 분석이 완료된 데이터입니다.");
        }
        // 2. [Non-Tx] AI 서버 요청 (🐢 가장 오래 걸리는 구간 - DB 커넥션 없이 수행)
        // String(key) 대신 DTO(Request) 객체를 생성해서 전달
        AiPredictionRequest requestDto = new AiPredictionRequest(rawDataFile.getS3ObjectKey());
        // 여기서 설정한 시간(타임아웃)이나 동시 요청 수(벌크헤드)를 넘기면 에러 발생 -> Fallback 이동
        AiPredictionResponse response = aiPredictionClient.requestPrediction(requestDto);


        // 3. 결과 처리 위임 (저장 + 알림) -> 여기서 트랜잭션이 시작됨
        predictionResultService.processResult(rawDataFile.getId(), user.getId(), response);

        return response;
    }
    /**
     * 🪂 Fallback 메서드 (장애 대응)
     * 서킷 브레이커나 벌크헤드에서 예외가 발생하면 실행됩니다.
     */
    public AiPredictionResponse fallbackPrediction(User user, Throwable t) {
        // 1. 벌크헤드 거절 (트래픽 폭주)
        if (t instanceof BulkheadFullException) {
            log.warn("✋ [Traffic Jam] 사용자(ID:{}) 요청 거절 - 동시 요청 한도 초과", user.getId());
            throw new ExternalApiException("현재 분석 요청이 너무 많아 대기 중입니다. 잠시 후 다시 시도해주세요.");
        }

        // 2. 서킷 브레이커 차단 (AI 서버 다운)
        if (t instanceof CallNotPermittedException) {
            log.error("⛔ [Circuit Open] AI 서버 차단됨. 사용자(ID:{}) 요청 즉시 실패 처리", user.getId());
            throw new ExternalApiException("AI 분석 서비스가 일시 점검 중입니다. 잠시 후 다시 시도해주세요.");
        }

        // 3. 타임아웃 (응답 지연)
        if (t instanceof TimeoutException) {
            log.error("⏳ [Timeout] AI 서버 응답 없음. 사용자(ID:{})", user.getId());
            throw new ExternalApiException("분석 시간이 너무 오래 걸리고 있습니다. 잠시 후 다시 시도해주세요.");
        }

        // 4. 그 외 알 수 없는 에러
        log.error("🛑 [AI Error] 알 수 없는 오류 발생. 원인: {}", t.getMessage());
        throw new ExternalApiException("AI 서비스 연결 중 오류가 발생했습니다.");
    }
}
