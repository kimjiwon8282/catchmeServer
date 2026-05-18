package com.example.catchme.scheduler;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${server.port:unknown}")
    private String serverPort;

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(
            name = "rawDataUploadRecoveryScheduler",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT30S"
    )
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
            log.info("[port={}] Raw 데이터 업로드 복구 대상 없음", serverPort);
            return;
        }

        log.info("[port={}] Raw 데이터 업로드 복구 대상 {}건 조회", serverPort, recoveryTargets.size());

        for (RawDataUploadJob uploadJob : recoveryTargets) {
            recoverOne(uploadJob);
        }
    }

    private void recoverOne(RawDataUploadJob uploadJob) {
        try {
            rawDataUploadRecoveryService.recover(uploadJob.getId());
            log.info("[port={}] Raw 데이터 업로드 복구 성공. uploadJobId={}", serverPort, uploadJob.getId());

        } catch (Exception e) {
            log.warn("[port={}] Raw 데이터 업로드 복구 실패. uploadJobId={}", serverPort, uploadJob.getId(), e);

            try {
                rawDataUploadJobService.markRecoveryFailed(uploadJob.getId(), e);
            } catch (Exception recordException) {
                log.error(
                        "[port={}] Raw 데이터 업로드 복구 실패 상태 기록 실패. uploadJobId={}",
                        serverPort,
                        uploadJob.getId(),
                        recordException
                );
            }
        }
    }
}