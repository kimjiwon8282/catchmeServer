package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ai_prediction_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 필수: 빈 객체 무분별한 생성 방지
public class AiPredictionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ==========================
       연관 관계 매핑
       ========================== */

    /**
     * 검사한 환자 (User)
     * - 보호자는 user.getLinkedUser()를 통해 환자를 찾고,
     * - 그 환자의 ID로 이 결과 테이블을 조회합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 분석된 원본 파일 (RawDataFile)
     * - 1:1 관계: 하나의 파일은 하나의 분석 결과를 가짐
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_data_file_id", nullable = false)
    private RawDataFile rawDataFile;

    /* ==========================
       AI 분석 결과 데이터
       ========================== */

    @Column(nullable = false)
    private Integer clusterId;   // AI가 분류한 유형 (1, 2, 3...)

    @Column(nullable = false)
    private Boolean isRisk;      // 위험군 여부 (true/false)

    @Column(nullable = false)
    private Double confidence;   // 확신도 (0.0 ~ 1.0)

    @Column(nullable = false)
    private LocalDateTime analyzedAt; // 분석 완료 시간

    /* ==========================
       생성자 (Builder)
       ========================== */
    @Builder
    public AiPredictionResult(User user, RawDataFile rawDataFile, Integer clusterId, Boolean isRisk, Double confidence) {
        this.user = user;
        this.rawDataFile = rawDataFile;
        this.clusterId = clusterId;
        this.isRisk = isRisk;
        this.confidence = confidence;

        // 이러면 별도의 설정 없이도 생성 시간이 기록됩니다.
        this.analyzedAt = LocalDateTime.now();
    }
}