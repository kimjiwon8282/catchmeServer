package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.interfaces.ai.AiPredictionClient;
import com.example.catchme.service.interfaces.ai.PredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionServiceImpl implements PredictionService {

    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionClient aiPredictionClient;
    private final PredictionResultService predictionResultService;

    @Override
    public AiPredictionResponse requestLatestPrediction(User user) {
        //조회
        RawDataFile rawDataFile = rawDataFileRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalStateException("측정 데이터가 없습니다."));

        if (rawDataFile.isAnalyzed()) {
            throw new IllegalStateException("이미 분석이 완료된 데이터입니다.");
        }
        // 2. [Non-Tx] AI 서버 요청 (🐢 가장 오래 걸리는 구간 - DB 커넥션 없이 수행)
        AiPredictionResponse response =
                aiPredictionClient.requestPrediction(rawDataFile.getS3ObjectKey());

        // 3. 결과 처리 위임 (저장 + 알림) -> 여기서 트랜잭션이 시작됨
        // user 객체 대신 userId를 넘겨서 안에서 다시 조회하게 하는 게 안전함
        predictionResultService.processResult(rawDataFile.getId(), user.getId(), response);

        return response;
    }
}
