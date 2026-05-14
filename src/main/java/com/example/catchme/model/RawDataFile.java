package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "raw_data_files", indexes = {
        @Index(name = "idx_user_created", columnList = "user_id, created_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawDataFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** S3 object key (파일 실제 위치) */
    @Column(nullable = false, length = 500, unique = true)
    private String s3ObjectKey;

    /** 업로드 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 분석 여부 */
    @Column(nullable = false)
    private boolean analyzed;

    private RawDataFile(User user, String s3ObjectKey) {
        this.user = user;
        this.s3ObjectKey = s3ObjectKey;
        this.createdAt = LocalDateTime.now();
        this.analyzed = false;
    }

    public static RawDataFile create(User user, String s3ObjectKey) {
        return new RawDataFile(user, s3ObjectKey);
    }

    public void markAnalyzed() {
        this.analyzed = true;
    }
}