package com.example.catchme.scheduler;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawDataUploadRecoveryScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final RawDataUploadJobRepository rawDataUploadJobRepository;
    private final RawDataUploadRecoveryService rawDataUploadRecoveryService;
    private final RawDataUploadJobService rawDataUploadJobService;

    @Scheduled(cron = "0 0 * * * *")
    public void recoverFailedRawDataUploads() {
        List<RawDataUploadJob> recoveryTargets =
                rawDataUploadJobRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                        List.of(
                                RawDataUploadStatus.DB_SAVE_FAILED,
                                RawDataUploadStatus.RECOVERY_FAILED
                        ),
                        MAX_RETRY_COUNT
                );

        if (recoveryTargets.isEmpty()) {
            return;
        }

        log.info("Raw 데이터 업로드 복구 대상 {}건 조회", recoveryTargets.size());

        for (RawDataUploadJob uploadJob : recoveryTargets) {
            recoverOne(uploadJob);
        }
    }

    private void recoverOne(RawDataUploadJob uploadJob) {
        try {
            rawDataUploadRecoveryService.recover(uploadJob.getId());
            log.info("Raw 데이터 업로드 복구 성공. uploadJobId={}", uploadJob.getId());

        } catch (Exception e) {
            log.warn("Raw 데이터 업로드 복구 실패. uploadJobId={}", uploadJob.getId(), e);

            try {
                rawDataUploadJobService.markRecoveryFailed(uploadJob.getId(), e);
            } catch (Exception recordException) {
                log.error(
                        "Raw 데이터 업로드 복구 실패 상태 기록 실패. uploadJobId={}",
                        uploadJob.getId(),
                        recordException
                );
            }
        }
    }
}