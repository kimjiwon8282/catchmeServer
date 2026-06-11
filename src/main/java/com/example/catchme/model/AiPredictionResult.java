package com.example.catchme.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ai_prediction_results", indexes = {
        @Index(name = "idx_prediction_user_analyzed", columnList = "user_id, analyzed_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPredictionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_data_file_id", nullable = false)
    private RawDataFile rawDataFile;

    @Column(nullable = false)
    private Integer clusterId;

    @Column(nullable = false)
    private Boolean isRisk;

    @Column(nullable = false)
    private Double confidence;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @Builder
    public AiPredictionResult(Member member, RawDataFile rawDataFile, Integer clusterId, Boolean isRisk, Double confidence) {
        this.member = member;
        this.rawDataFile = rawDataFile;
        this.clusterId = clusterId;
        this.isRisk = isRisk;
        this.confidence = confidence;
        this.analyzedAt = LocalDateTime.now();
    }
}
