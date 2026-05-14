package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "raw_data_upload_jobs", indexes = {
        @Index(name = "idx_raw_upload_status_retry_created", columnList = "status, retry_count, created_at"),
        @Index(name = "idx_raw_upload_user_created", columnList = "user_id, created_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawDataUploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 업로드 요청 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** S3 object key */
    @Column(name = "s3_object_key", nullable = false, length = 500)
    private String s3ObjectKey;

    /** 업로드 작업 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RawDataUploadStatus status;

    /** 실패 사유 */
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** 재시도 횟수 */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    private RawDataUploadJob(User user, String s3ObjectKey) {
        this.user = user;
        this.s3ObjectKey = s3ObjectKey;
        this.status = RawDataUploadStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static RawDataUploadJob create(User user, String s3ObjectKey) {
        return new RawDataUploadJob(user, s3ObjectKey);
    }

    public void markS3Uploaded() {
        this.status = RawDataUploadStatus.S3_UPLOADED;
        this.failureReason = null;
        touch();
    }

    public void markS3UploadFailed(String failureReason) {
        this.status = RawDataUploadStatus.S3_UPLOAD_FAILED;
        this.failureReason = failureReason;
        touch();
    }

    public void markDbSaveFailed(String failureReason) {
        this.status = RawDataUploadStatus.DB_SAVE_FAILED;
        this.failureReason = failureReason;
        touch();
    }

    public void markCompleted() {
        this.status = RawDataUploadStatus.COMPLETED;
        this.failureReason = null;
        touch();
    }

    public void markRecoveryFailed(String failureReason) {
        this.status = RawDataUploadStatus.RECOVERY_FAILED;
        this.failureReason = failureReason;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}