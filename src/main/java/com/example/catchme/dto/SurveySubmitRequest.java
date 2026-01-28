package com.example.catchme.dto;

import com.example.catchme.model.SurveyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SurveySubmitRequest {
    @NotNull(message = "설문 타입은 필수입니다.")
    private SurveyType type;       // SMCQ 또는 K_AD8

    @Min(value = 0, message = "점수는 0점 이상이어야 합니다.")
    private int totalScore;        // 계산된 총점

    private String answersJson;    // 상세 답변 (예: {"1":1, "2":0 ...})
}
