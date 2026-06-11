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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    @Column(name = "s3_object_key", nullable = false, length = 500)
    private String s3ObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RawDataUploadStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    private RawDataUploadJob(Member member, String s3ObjectKey) {
        this.member = member;
        this.s3ObjectKey = s3ObjectKey;
        this.status = RawDataUploadStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static RawDataUploadJob create(Member member, String s3ObjectKey) {
        return new RawDataUploadJob(member, s3ObjectKey);
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
