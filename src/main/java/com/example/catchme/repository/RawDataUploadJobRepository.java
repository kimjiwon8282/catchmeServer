package com.example.catchme.repository;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RawDataUploadJobRepository extends JpaRepository<RawDataUploadJob, Long> {
    //복구 대상 조회 메서드
    List<RawDataUploadJob> findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<RawDataUploadStatus> statuses,
            int retryCount
    );
}