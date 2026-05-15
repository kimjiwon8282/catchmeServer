package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.scheduler.RawDataUploadRecoveryScheduler;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataUploadRecoverySchedulerTest {

    @Mock
    private RawDataUploadJobRepository rawDataUploadJobRepository;

    @Mock
    private RawDataUploadRecoveryService rawDataUploadRecoveryService;

    @Mock
    private RawDataUploadJobService rawDataUploadJobService;

    @InjectMocks
    private RawDataUploadRecoveryScheduler rawDataUploadRecoveryScheduler;

    @Nested
    @DisplayName("recoverFailedRawDataUploads")
    class RecoverFailedRawDataUploads {

        @Test
        @DisplayName("DB_SAVE_FAILED와 RECOVERY_FAILED 중 retryCount가 3 미만인 작업을 조회한다")
        void findRecoverableJobs() {
            when(rawDataUploadJobRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                    org.mockito.ArgumentMatchers.anyCollection(),
                    eq(3)
            )).thenReturn(List.of());

            rawDataUploadRecoveryScheduler.recoverFailedRawDataUploads();

            ArgumentCaptor<Collection<RawDataUploadStatus>> statusesCaptor =
                    ArgumentCaptor.forClass(Collection.class);

            verify(rawDataUploadJobRepository)
                    .findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                            statusesCaptor.capture(),
                            eq(3)
                    );

            assertThat(statusesCaptor.getValue())
                    .containsExactlyInAnyOrder(
                            RawDataUploadStatus.DB_SAVE_FAILED,
                            RawDataUploadStatus.RECOVERY_FAILED
                    );

            verify(rawDataUploadRecoveryService, never()).recover(org.mockito.ArgumentMatchers.anyLong());
            verify(rawDataUploadJobService, never()).markRecoveryFailed(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any(Exception.class)
            );
        }

        @Test
        @DisplayName("조회된 복구 대상마다 recover를 호출한다")
        void recoverEachTargetJob() {
            RawDataUploadJob job1 = uploadJob(1L);
            RawDataUploadJob job2 = uploadJob(2L);

            when(rawDataUploadJobRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                    org.mockito.ArgumentMatchers.anyCollection(),
                    eq(3)
            )).thenReturn(List.of(job1, job2));

            rawDataUploadRecoveryScheduler.recoverFailedRawDataUploads();

            verify(rawDataUploadRecoveryService).recover(1L);
            verify(rawDataUploadRecoveryService).recover(2L);

            verify(rawDataUploadJobService, never()).markRecoveryFailed(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any(Exception.class)
            );
        }

        @Test
        @DisplayName("개별 복구가 실패하면 RECOVERY_FAILED 상태로 기록하고 다음 작업 처리를 계속한다")
        void markRecoveryFailedWhenRecoverFailsAndContinueNextJob() {
            RawDataUploadJob job1 = uploadJob(1L);
            RawDataUploadJob job2 = uploadJob(2L);

            RuntimeException exception = new RuntimeException("recovery failed");

            when(rawDataUploadJobRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                    org.mockito.ArgumentMatchers.anyCollection(),
                    eq(3)
            )).thenReturn(List.of(job1, job2));

            doThrow(exception)
                    .when(rawDataUploadRecoveryService)
                    .recover(1L);

            rawDataUploadRecoveryScheduler.recoverFailedRawDataUploads();

            verify(rawDataUploadRecoveryService).recover(1L);
            verify(rawDataUploadRecoveryService).recover(2L);

            verify(rawDataUploadJobService).markRecoveryFailed(1L, exception);
            verify(rawDataUploadJobService, never()).markRecoveryFailed(
                    eq(2L),
                    org.mockito.ArgumentMatchers.any(Exception.class)
            );
        }

        @Test
        @DisplayName("복구 실패 상태 기록까지 실패해도 스케줄러는 예외를 밖으로 던지지 않는다")
        void doNotThrowWhenMarkRecoveryFailedAlsoFails() {
            RawDataUploadJob job = uploadJob(1L);

            RuntimeException recoveryException = new RuntimeException("recovery failed");
            RuntimeException recordException = new RuntimeException("record recovery failed");

            when(rawDataUploadJobRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                    org.mockito.ArgumentMatchers.anyCollection(),
                    eq(3)
            )).thenReturn(List.of(job));

            doThrow(recoveryException)
                    .when(rawDataUploadRecoveryService)
                    .recover(1L);

            doThrow(recordException)
                    .when(rawDataUploadJobService)
                    .markRecoveryFailed(1L, recoveryException);

            rawDataUploadRecoveryScheduler.recoverFailedRawDataUploads();

            verify(rawDataUploadRecoveryService).recover(1L);
            verify(rawDataUploadJobService).markRecoveryFailed(1L, recoveryException);
        }
    }

    private RawDataUploadJob uploadJob(Long id) {
        User user = User.builder()
                .email("user@catchme.com")
                .password("encodedPassword")
                .name("지원")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", 1L);

        RawDataUploadJob uploadJob = RawDataUploadJob.create(
                user,
                "raw-data/user-1/20260514_221000_" + id + ".csv"
        );

        ReflectionTestUtils.setField(uploadJob, "id", id);
        uploadJob.markDbSaveFailed("metadata save failed");

        return uploadJob;
    }
}