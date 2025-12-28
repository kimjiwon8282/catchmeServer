package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "raw_data_files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawDataFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 */
    @ManyToOne(fetch = FetchType.LAZY) //User중심 설계
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** S3 object key (파일 실제 위치) */
    @Column(nullable = false, length = 500)
    private String s3ObjectKey; //objectKey만 저장, 파일은 S3책임

    /** 업로드 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 분석 여부 (확장 대비) */
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
