package com.example.catchme.dto;

import com.example.catchme.model.AiPredictionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class PredictionHistoryResponse {
    private Long id;
    private LocalDateTime analyzedAt; // 분석 날짜
    private boolean isRisk;           // 위험군 여부
    private double confidence;        // 확신도
    private int clusterId;            // 클러스터 ID

    public static PredictionHistoryResponse from(AiPredictionResult entity) {
        return PredictionHistoryResponse.builder()
                .id(entity.getId())
                .analyzedAt(entity.getAnalyzedAt())
                .isRisk(entity.getIsRisk())
                .confidence(entity.getConfidence())
                .clusterId(entity.getClusterId())
                .build();
    }
}
