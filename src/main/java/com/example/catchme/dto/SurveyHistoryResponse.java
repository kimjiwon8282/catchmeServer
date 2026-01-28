package com.example.catchme.dto;

import com.example.catchme.model.SurveyResult;
import com.example.catchme.model.SurveyType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SurveyHistoryResponse {
    private Long id;
    private SurveyType type;       // SMCQ, K_AD8
    private int totalScore;        // 총점
    private boolean isRisk;        // 위험군 여부
    private LocalDateTime createdAt; // 검사 일시

    // Entity -> DTO 변환 메서드
    public static SurveyHistoryResponse from(SurveyResult entity) {
        return SurveyHistoryResponse.builder()
                .id(entity.getId())
                .type(entity.getType())
                .totalScore(entity.getTotalScore())
                .isRisk(entity.isRisk())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
