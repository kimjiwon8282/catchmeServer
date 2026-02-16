package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPredictionConsumer {

    // 🌟 여기서 기존에 완벽하게 짜두신 서비스를 그대로 재사용합니다!
    private final PredictionResultService predictionResultService;

    @SqsListener("ai-prediction-request-queue")
    public void processAiPrediction(AiPredictionRequestEvent event) {
        log.info("📥 [Consumer] SQS에서 AI 분석 요청 꺼냄! 작업 시작... (userId: {})", event.getUserId());

        try {
            // =========================================================
            // 🐢 무거운 AI 서버 요청 시뮬레이션 (3초 지연)
            // =========================================================
            log.info("🐢 [Mock AI] 3초 지연 시작...");
            Thread.sleep(3000);

            // 가짜 응답 생성
            AiPredictionResponse response = new AiPredictionResponse(1, true, 98.5);
            log.info("✅ [Mock AI] 분석 완료!");

            // 🌟 기존 코드 완벽 재사용: DB 저장 및 푸시 알림 발송 트리거
            predictionResultService.processResult(event.getRawDataFileId(), event.getUserId(), response);

        } catch (InterruptedException e) {
            log.error("❌ AI 분석 중 스레드 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("❌ AI 분석 처리 중 에러 발생", e);
            throw new RuntimeException("AI 분석 실패", e);
        }
    }
}
