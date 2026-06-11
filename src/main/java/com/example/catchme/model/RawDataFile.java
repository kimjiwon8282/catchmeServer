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
import jakarta.persistence.Table;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 500, unique = true)
    private String s3ObjectKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean analyzed;

    private RawDataFile(Member member, String s3ObjectKey) {
        this.member = member;
        this.s3ObjectKey = s3ObjectKey;
        this.createdAt = LocalDateTime.now();
        this.analyzed = false;
    }

    public static RawDataFile create(Member member, String s3ObjectKey) {
        return new RawDataFile(member, s3ObjectKey);
    }

    public void markAnalyzed() {
        this.analyzed = true;
    }
}
