package com.example.catchme.service.impl.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.exception.exceptions.IllegalCsvCreateException;
import com.example.catchme.exception.exceptions.RawDataMetadataSaveFailException;
import com.example.catchme.exception.exceptions.S3UploadFailException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private RawDataMetadataService rawDataMetadataService;

    @Mock
    private RawDataUploadJobService rawDataUploadJobService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RawDataServiceImpl rawDataService;

    @Nested
    @DisplayName("uploadRawDataAsCsv")
    class UploadRawDataAsCsv {

        @Test
        @DisplayName("CSV를 생성해 S3에 업로드하고 메타데이터 저장 완료 상태로 기록한다")
        void uploadRawDataAsCsvSuccess() throws Exception {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333),
                    rawSensorDataRequest("2026-03-26T10:00:01", 11, 21, 31, 41, 0.444, 0.555, 0.666)
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            when(rawDataUploadJobService.createPendingJob(eq(user), anyString()))
                    .thenAnswer(invocation -> {
                        String objectKey = invocation.getArgument(1);
                        return uploadJob(1L, user, objectKey);
                    });

            AtomicReference<String> csvContent = new AtomicReference<>();

            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenAnswer(invocation -> {
                        Path csvPath = invocation.getArgument(0);
                        String objectKey = invocation.getArgument(1);

                        csvContent.set(Files.readString(csvPath));

                        return objectKey;
                    });

            RawDataUploadResponse response = rawDataService.uploadRawDataAsCsv(userId, requests);

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);

            verify(fileStorageService).uploadCsv(pathCaptor.capture(), objectKeyCaptor.capture());

            Path uploadedPath = pathCaptor.getValue();
            String objectKey = objectKeyCaptor.getValue();

            assertThat(response.getObjectKey()).isEqualTo(objectKey);
            assertThat(objectKey)
                    .startsWith("raw-data/user-1/")
                    .endsWith(".csv");
            assertThat(objectKey).contains("_");

            assertThat(csvContent.get())
                    .contains("timestamp,p1,p2,p3,p4,acc_x,acc_y,acc_z")
                    .contains("2026-03-26T10:00:00,10,20,30,40,0.111,0.222,0.333")
                    .contains("2026-03-26T10:00:01,11,21,31,41,0.444,0.555,0.666");

            assertThat(Files.exists(uploadedPath)).isFalse();

            verify(rawDataUploadJobService).createPendingJob(user, objectKey);
            verify(rawDataUploadJobService).markS3Uploaded(1L);
            verify(rawDataMetadataService).save(user, objectKey);
            verify(rawDataUploadJobService).markCompleted(1L);

            verify(rawDataUploadJobService, never()).markS3UploadFailed(eq(1L), any(Exception.class));
            verify(rawDataUploadJobService, never()).markDbSaveFailed(eq(1L), any(Exception.class));
            verify(rawDataUploadJobService, never()).markRecoveryFailed(eq(1L), any(Exception.class));
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 업로드 작업을 생성하지 않는다")
        void uploadRawDataAsCsvFailsWhenUserNotFound() {
            Long userId = 999L;

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, List.of()))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(rawDataUploadJobService, never()).createPendingJob(any(User.class), anyString());
            verify(fileStorageService, never()).uploadCsv(any(Path.class), anyString());
            verify(rawDataMetadataService, never()).save(any(User.class), anyString());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("CSV 생성에 실패하면 업로드 작업을 생성하지 않고 S3 업로드도 진행하지 않는다")
        void uploadRawDataAsCsvFailsWhenCsvCreationFails() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, null))
                    .isInstanceOf(IllegalCsvCreateException.class)
                    .hasMessage("CSV 파일 생성에 실패했습니다.");

            verify(rawDataUploadJobService, never()).createPendingJob(any(User.class), anyString());
            verify(rawDataUploadJobService, never()).markS3Uploaded(any());
            verify(rawDataUploadJobService, never()).markS3UploadFailed(any(), any(Exception.class));
            verify(rawDataUploadJobService, never()).markDbSaveFailed(any(), any(Exception.class));
            verify(rawDataUploadJobService, never()).markCompleted(any());

            verify(fileStorageService, never()).uploadCsv(any(Path.class), anyString());
            verify(rawDataMetadataService, never()).save(any(User.class), anyString());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("S3 업로드가 일시적으로 실패해도 3회 이내 성공하면 S3_UPLOADED와 COMPLETED 상태로 진행한다")
        void uploadRawDataAsCsvRetriesS3UploadAndSucceeds() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333)
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            when(rawDataUploadJobService.createPendingJob(eq(user), anyString()))
                    .thenAnswer(invocation -> {
                        String objectKey = invocation.getArgument(1);
                        return uploadJob(1L, user, objectKey);
                    });

            AtomicInteger attempt = new AtomicInteger(0);

            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenAnswer(invocation -> {
                        int currentAttempt = attempt.incrementAndGet();

                        if (currentAttempt < 3) {
                            throw new RuntimeException("temporary s3 error");
                        }

                        return invocation.getArgument(1);
                    });

            RawDataUploadResponse response = rawDataService.uploadRawDataAsCsv(userId, requests);

            ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(fileStorageService, times(3)).uploadCsv(any(Path.class), objectKeyCaptor.capture());

            String objectKey = objectKeyCaptor.getAllValues().get(0);

            assertThat(response.getObjectKey()).isEqualTo(objectKey);
            assertThat(attempt.get()).isEqualTo(3);

            verify(rawDataUploadJobService).createPendingJob(user, objectKey);
            verify(rawDataUploadJobService).markS3Uploaded(1L);
            verify(rawDataMetadataService).save(user, objectKey);
            verify(rawDataUploadJobService).markCompleted(1L);

            verify(rawDataUploadJobService, never()).markS3UploadFailed(any(), any(Exception.class));
            verify(rawDataUploadJobService, never()).markDbSaveFailed(any(), any(Exception.class));
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("S3 업로드가 3회 모두 실패하면 S3_UPLOAD_FAILED 상태로 기록하고 S3UploadFailException을 던진다")
        void uploadRawDataAsCsvWrapsS3UploadFailureAfterThreeAttempts() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333)
            );

            RuntimeException originalException = new RuntimeException("low level s3 error");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            when(rawDataUploadJobService.createPendingJob(eq(user), anyString()))
                    .thenAnswer(invocation -> {
                        String objectKey = invocation.getArgument(1);
                        return uploadJob(1L, user, objectKey);
                    });

            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenThrow(originalException);

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, requests))
                    .isInstanceOf(S3UploadFailException.class)
                    .hasMessage("Raw 데이터 S3 업로드에 실패했습니다.")
                    .hasCauseInstanceOf(S3UploadFailException.class);

            verify(rawDataUploadJobService).createPendingJob(eq(user), anyString());
            verify(fileStorageService, times(3)).uploadCsv(any(Path.class), anyString());

            verify(rawDataUploadJobService).markS3UploadFailed(eq(1L), any(S3UploadFailException.class));

            verify(rawDataUploadJobService, never()).markS3Uploaded(any());
            verify(rawDataMetadataService, never()).save(any(User.class), anyString());
            verify(rawDataUploadJobService, never()).markDbSaveFailed(any(), any(Exception.class));
            verify(rawDataUploadJobService, never()).markCompleted(any());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("메타데이터 저장에 실패하면 S3 파일을 삭제하지 않고 DB_SAVE_FAILED 상태로 기록한다")
        void uploadRawDataAsCsvMarksDbSaveFailedWithoutDeletingS3WhenMetadataSaveFails() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333)
            );

            RuntimeException metadataException = new RuntimeException("metadata save failed");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            when(rawDataUploadJobService.createPendingJob(eq(user), anyString()))
                    .thenAnswer(invocation -> {
                        String objectKey = invocation.getArgument(1);
                        return uploadJob(1L, user, objectKey);
                    });

            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

            doThrow(metadataException)
                    .when(rawDataMetadataService)
                    .save(eq(user), anyString());

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, requests))
                    .isInstanceOf(RawDataMetadataSaveFailException.class)
                    .hasMessage("Raw 데이터는 S3에 저장되었지만 메타데이터 저장에 실패했습니다. 이후 복구 작업을 통해 재처리됩니다.")
                    .hasCause(metadataException);

            ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(fileStorageService).uploadCsv(any(Path.class), objectKeyCaptor.capture());

            String objectKey = objectKeyCaptor.getValue();

            verify(rawDataUploadJobService).createPendingJob(user, objectKey);
            verify(rawDataUploadJobService).markS3Uploaded(1L);
            verify(rawDataMetadataService).save(user, objectKey);
            verify(rawDataUploadJobService).markDbSaveFailed(1L, metadataException);

            verify(rawDataUploadJobService, never()).markCompleted(any());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }
    }

    private RawDataUploadJob uploadJob(Long id, User user, String objectKey) {
        RawDataUploadJob uploadJob = RawDataUploadJob.create(user, objectKey);
        ReflectionTestUtils.setField(uploadJob, "id", id);
        return uploadJob;
    }

    private User user(Long id, String email) {
        User user = User.builder()
                .email(email)
                .password("encodedPassword")
                .name("지원")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private RawSensorDataRequest rawSensorDataRequest(
            String timestamp,
            int pressure1,
            int pressure2,
            int pressure3,
            int pressure4,
            double accX,
            double accY,
            double accZ
    ) {
        RawSensorDataRequest request = new RawSensorDataRequest();

        ReflectionTestUtils.setField(request, "timestamp", timestamp);
        ReflectionTestUtils.setField(request, "pressure1", pressure1);
        ReflectionTestUtils.setField(request, "pressure2", pressure2);
        ReflectionTestUtils.setField(request, "pressure3", pressure3);
        ReflectionTestUtils.setField(request, "pressure4", pressure4);
        ReflectionTestUtils.setField(request, "accX", accX);
        ReflectionTestUtils.setField(request, "accY", accY);
        ReflectionTestUtils.setField(request, "accZ", accZ);

        return request;
    }
}