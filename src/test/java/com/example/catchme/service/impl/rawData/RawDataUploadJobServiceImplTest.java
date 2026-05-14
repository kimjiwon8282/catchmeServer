package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataUploadJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataUploadJobServiceImplTest {

    @Mock
    private RawDataUploadJobRepository rawDataUploadJobRepository;

    @InjectMocks
    private RawDataUploadJobServiceImpl rawDataUploadJobService;

    @Nested
    @DisplayName("createPendingJob")
    class CreatePendingJob {

        @Test
        @DisplayName("PENDING 상태의 업로드 작업을 생성해 저장한다")
        void createPendingJob() {
            User user = user(1L);
            String objectKey = "raw-data/user-1/20260514_221000_abcd1234.csv";

            when(rawDataUploadJobRepository.save(any(RawDataUploadJob.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RawDataUploadJob result = rawDataUploadJobService.createPendingJob(user, objectKey);

            ArgumentCaptor<RawDataUploadJob> captor = ArgumentCaptor.forClass(RawDataUploadJob.class);
            verify(rawDataUploadJobRepository).save(captor.capture());

            RawDataUploadJob savedJob = captor.getValue();

            assertThat(result).isSameAs(savedJob);
            assertThat(savedJob.getUser()).isEqualTo(user);
            assertThat(savedJob.getS3ObjectKey()).isEqualTo(objectKey);
            assertThat(savedJob.getStatus()).isEqualTo(RawDataUploadStatus.PENDING);
            assertThat(savedJob.getRetryCount()).isZero();
            assertThat(savedJob.getFailureReason()).isNull();
            assertThat(savedJob.getCreatedAt()).isNotNull();
            assertThat(savedJob.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("상태 변경")
    class MarkStatus {

        @Test
        @DisplayName("markS3Uploaded는 상태를 S3_UPLOADED로 변경하고 실패 사유를 초기화한다")
        void markS3Uploaded() {
            RawDataUploadJob uploadJob = uploadJob(1L);

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markS3Uploaded(1L);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.S3_UPLOADED);
            assertThat(uploadJob.getFailureReason()).isNull();
            assertThat(uploadJob.getUpdatedAt()).isNotNull();

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("markS3UploadFailed는 상태를 S3_UPLOAD_FAILED로 변경하고 실패 사유를 저장한다")
        void markS3UploadFailed() {
            RawDataUploadJob uploadJob = uploadJob(1L);
            RuntimeException exception = new RuntimeException("s3 upload failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markS3UploadFailed(1L, exception);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.S3_UPLOAD_FAILED);
            assertThat(uploadJob.getFailureReason()).isEqualTo("s3 upload failed");

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("markDbSaveFailed는 상태를 DB_SAVE_FAILED로 변경하고 실패 사유를 저장한다")
        void markDbSaveFailed() {
            RawDataUploadJob uploadJob = uploadJob(1L);
            RuntimeException exception = new RuntimeException("metadata save failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markDbSaveFailed(1L, exception);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.DB_SAVE_FAILED);
            assertThat(uploadJob.getFailureReason()).isEqualTo("metadata save failed");

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("markCompleted는 상태를 COMPLETED로 변경하고 실패 사유를 초기화한다")
        void markCompleted() {
            RawDataUploadJob uploadJob = uploadJob(1L);
            uploadJob.markDbSaveFailed("metadata save failed");

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markCompleted(1L);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.COMPLETED);
            assertThat(uploadJob.getFailureReason()).isNull();

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("실패 사유가 비어 있으면 예외 클래스명을 저장한다")
        void markDbSaveFailedUsesExceptionClassNameWhenMessageIsBlank() {
            RawDataUploadJob uploadJob = uploadJob(1L);
            RuntimeException exception = new RuntimeException();

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markDbSaveFailed(1L, exception);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.DB_SAVE_FAILED);
            assertThat(uploadJob.getFailureReason()).isEqualTo("RuntimeException");

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("실패 사유가 1000자를 넘으면 1000자로 잘라 저장한다")
        void markDbSaveFailedTruncatesLongFailureReason() {
            RawDataUploadJob uploadJob = uploadJob(1L);
            String longMessage = "a".repeat(1100);
            RuntimeException exception = new RuntimeException(longMessage);

            when(rawDataUploadJobRepository.findById(1L))
                    .thenReturn(Optional.of(uploadJob));

            rawDataUploadJobService.markDbSaveFailed(1L, exception);

            assertThat(uploadJob.getStatus()).isEqualTo(RawDataUploadStatus.DB_SAVE_FAILED);
            assertThat(uploadJob.getFailureReason()).hasSize(1000);
            assertThat(uploadJob.getFailureReason()).isEqualTo("a".repeat(1000));

            verify(rawDataUploadJobRepository).findById(1L);
        }

        @Test
        @DisplayName("업로드 작업을 찾을 수 없으면 IllegalArgumentException을 던진다")
        void throwsExceptionWhenUploadJobNotFound() {
            when(rawDataUploadJobRepository.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rawDataUploadJobService.markCompleted(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("업로드 작업을 찾을 수 없습니다.");

            verify(rawDataUploadJobRepository).findById(999L);
        }
    }

    private RawDataUploadJob uploadJob(Long id) {
        User user = user(1L);
        RawDataUploadJob uploadJob = RawDataUploadJob.create(
                user,
                "raw-data/user-1/20260514_221000_abcd1234.csv"
        );

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