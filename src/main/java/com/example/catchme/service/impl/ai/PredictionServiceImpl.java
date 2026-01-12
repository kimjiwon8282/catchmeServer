package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.interfaces.ai.AiPredictionClient;
import com.example.catchme.service.interfaces.ai.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionServiceImpl implements PredictionService {

    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionClient aiPredictionClient;

    @Override
    @Transactional
    public AiPredictionResponse requestLatestPrediction(User user) {

        RawDataFile rawDataFile = rawDataFileRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalStateException("측정 데이터가 없습니다."));

        if (rawDataFile.isAnalyzed()) {
            throw new IllegalStateException("이미 분석이 완료된 데이터입니다.");
        }

        AiPredictionResponse response =
                aiPredictionClient.requestPrediction(rawDataFile.getS3ObjectKey());

        rawDataFile.markAnalyzed();

        return response;
    }
}
