package com.example.catchme.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "survey_results", indexes = {
        @Index(name = "idx_survey_user_created", columnList = "user_id, created_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurveyType type;

    @Column(nullable = false)
    private int totalScore;

    @Column(nullable = false)
    private boolean isRisk;

    @Column(columnDefinition = "TEXT")
    private String answersJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public SurveyResult(Member member, SurveyType type, int totalScore, boolean isRisk, String answersJson) {
        this.member = member;
        this.type = type;
        this.totalScore = totalScore;
        this.isRisk = isRisk;
        this.answersJson = answersJson;
        this.createdAt = LocalDateTime.now();
    }
}
