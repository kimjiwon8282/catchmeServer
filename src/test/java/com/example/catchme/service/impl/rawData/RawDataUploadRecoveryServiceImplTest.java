package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataUploadRecoveryServiceImplTest {

    @Mock
    private RawDataUploadJobRepository rawDataUploadJobRepository;

    @Mock
    private RawDataFileRepository rawDataFileRepository;

    @Mock
    private RawDataMetadataService rawDataMetadataService;

    @InjectMocks
    private RawDataUploadRecoveryServiceImpl rawDataUploadRecoveryService;

    @Nested
    @DisplayName("recover")
    class Recover {

        @Test
        @DisplayName("DB_SAVE_FAILED 작업은 RawDataFile 메타데이터를 저장하고 COMPLETED 상태로 변경한다")
        void recoverDbSaveFailedJob() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";
            RawDataUploadJob uploadJob = uploadJob(1L, user, objectKey);
            uploadJob.markDbSaveFailed("metadata save failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));
            when(rawDataFileRepository.existsByS3ObjectKey(objectKey))
                    .thenReturn(false);

            rawDataUploadRecoveryService.recover(1L);

            verify(rawDataFileRepository).existsByS3ObjectKey(objectKey);
            verify(rawDataMetadataService).save(user, objectKey);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.COMPLETED);
            assertThat(uploadJob.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("RECOVERY_FAILED 작업도 재복구 대상이면 RawDataFile 메타데이터를 저장하고 COMPLETED 상태로 변경한다")
        void recoverRecoveryFailedJob() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";
            RawDataUploadJob uploadJob = uploadJob(1L, user, objectKey);
            uploadJob.markRecoveryFailed("previous recovery failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));
            when(rawDataFileRepository.existsByS3ObjectKey(objectKey))
                    .thenReturn(false);

            rawDataUploadRecoveryService.recover(1L);

            verify(rawDataFileRepository).existsByS3ObjectKey(objectKey);
            verify(rawDataMetadataService).save(user, objectKey);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.COMPLETED);
            assertThat(uploadJob.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("이미 RawDataFile 메타데이터가 있으면 중복 저장하지 않고 COMPLETED 상태로 변경한다")
        void recoverWithoutDuplicateMetadataSaveWhenRawDataFileAlreadyExists() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";
            RawDataUploadJob uploadJob = uploadJob(1L, user, objectKey);
            uploadJob.markDbSaveFailed("metadata save failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));
            when(rawDataFileRepository.existsByS3ObjectKey(objectKey))
                    .thenReturn(true);

            rawDataUploadRecoveryService.recover(1L);

            verify(rawDataFileRepository).existsByS3ObjectKey(objectKey);
            verify(rawDataMetadataService, never()).save(user, objectKey);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.COMPLETED);
            assertThat(uploadJob.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("복구 대상 상태가 아니면 메타데이터 저장과 상태 변경을 하지 않는다")
        void doNothingWhenJobStatusIsNotRecoverable() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";
            RawDataUploadJob uploadJob = uploadJob(1L, user, objectKey);
            uploadJob.markS3UploadFailed("s3 upload failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadRecoveryService.recover(1L);

            verify(rawDataFileRepository, never()).existsByS3ObjectKey(objectKey);
            verify(rawDataMetadataService, never()).save(user, objectKey);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.S3_UPLOAD_FAILED);
            assertThat(uploadJob.getFailureReason()).isEqualTo("s3 upload failed");
        }

        @Test
        @DisplayName("복구 대상 작업을 찾을 수 없으면 IllegalArgumentException을 던진다")
        void throwsExceptionWhenUploadJobNotFound() {
            when(rawDataUploadJobRepository.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rawDataUploadRecoveryService.recover(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("업로드 작업을 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("메타데이터 저장이 다시 실패하면 예외를 전파하고 COMPLETED 상태로 변경하지 않는다")
        void propagateExceptionWhenMetadataSaveFailsAgain() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";
            RawDataUploadJob uploadJob = uploadJob(1L, user, objectKey);
            uploadJob.markDbSaveFailed("metadata save failed");

            RuntimeException exception = new RuntimeException("recovery metadata save failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));
            when(rawDataFileRepository.existsByS3ObjectKey(objectKey))
                    .thenReturn(false);
            doThrow(exception)
                    .when(rawDataMetadataService)
                    .save(user, objectKey);

            assertThatThrownBy(() -> rawDataUploadRecoveryService.recover(1L))
                    .isSameAs(exception);

            verify(rawDataFileRepository).existsByS3ObjectKey(objectKey);
            verify(rawDataMetadataService).save(user, objectKey);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.DB_SAVE_FAILED);
            assertThat(uploadJob.getFailureReason()).isEqualTo("metadata save failed");
        }
    }

    private RawDataUploadJob uploadJob(Long id, User user, String objectKey) {
        RawDataUploadJob uploadJob = RawDataUploadJob.create(user, objectKey);
        ReflectionTestUtils.setField(uploadJob, "id", id);
        return uploadJob;
    }

    private User user(Long id) {
        User user = User.builder()
                .email("user@catchme.com")
                .password("encodedPassword")
                .name("지원")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }
}