package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "survey_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ==========================
       연관 관계
       ========================== */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /* ==========================
       설문 데이터
       ========================== */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurveyType type; // SMCQ 인지 K-AD8 인지

    @Column(nullable = false)
    private int totalScore; // 총점

    @Column(nullable = false)
    private boolean isRisk; // 위험군 판정 여부 (true: 위험, false: 정상)

    @Column(columnDefinition = "TEXT")
    private String answersJson; // 상세 답변 (예: {"1":1, "2":0 ...}) - 확장성 고려

    @Column(nullable = false)
    private LocalDateTime createdAt; // 검사 일시

    @Builder
    public SurveyResult(User user, SurveyType type, int totalScore, boolean isRisk, String answersJson) {
        this.user = user;
        this.type = type;
        this.totalScore = totalScore;
        this.isRisk = isRisk;
        this.answersJson = answersJson;
        this.createdAt = LocalDateTime.now(); // 생성 시 현재 시간 자동 기록
    }
}