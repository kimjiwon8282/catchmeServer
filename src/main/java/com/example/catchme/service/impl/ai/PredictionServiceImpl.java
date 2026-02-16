package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.ai.PredictionService;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionServiceImpl implements PredictionService {

    private final RawDataFileRepository rawDataFileRepository;
    private final UserRepository userRepository;
    private final SqsTemplate sqsTemplate; // 👈 SQS 템플릿 주입

    // 더 이상 PredictionResultService나 AiPredictionClient를 여기서 주입받지 않습니다!

    @Override
    public String requestLatestPrediction(Long userId) {

        // 1. 유저 조회 및 검증 (초고속 ⚡)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        RawDataFile rawDataFile = rawDataFileRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalStateException("측정 데이터가 없습니다."));

        if (rawDataFile.isAnalyzed()) {
            throw new IllegalStateException("이미 분석이 완료된 데이터입니다.");
        }

        // 2. 큐에 던질 편지 봉투 생성
        AiPredictionRequestEvent event = new AiPredictionRequestEvent(
                user.getId(),
                rawDataFile.getId(),
                rawDataFile.getS3ObjectKey()
        );

        // 3. SQS 큐에 던지기 (0.01초 컷 ⚡)
        sqsTemplate.send(to -> to
                .queue("ai-prediction-request-queue")
                .payload(event)
        );

        log.info("📤 [Producer] AI 분석 요청을 SQS에 접수했습니다! (스레드 즉시 반환)");

        // 4. 대기하지 않고 바로 리턴
        return "AI 분석 요청이 성공적으로 접수되었습니다. 완료 시 푸시 알림으로 알려드립니다.";
    }
}