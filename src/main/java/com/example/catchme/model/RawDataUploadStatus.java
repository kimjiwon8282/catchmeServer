package com.example.catchme.model;

public enum RawDataUploadStatus {
    PENDING,            // 업로드 작업 생성
    S3_UPLOADED,        // S3 업로드 성공
    S3_UPLOAD_FAILED,   // S3 업로드 실패
    DB_SAVE_FAILED,     // S3 업로드는 성공했지만 DB 메타데이터 저장 실패
    COMPLETED,          // S3 업로드 + DB 메타데이터 저장 완료
    RECOVERY_FAILED     // 추후 스케줄러 재처리 실패
}