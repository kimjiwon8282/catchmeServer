package com.example.catchme.service.impl.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.exception.exceptions.IllegalCsvCreateException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private RawDataMetadataService rawDataMetadataService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RawDataServiceImpl rawDataService;

    @Nested
    @DisplayName("uploadRawDataAsCsv")
    class UploadRawDataAsCsv {

        @Test
        @DisplayName("CSV를 생성해 S3에 업로드하고 메타데이터를 저장한 뒤 objectKey를 반환하며 로컬 임시 파일을 삭제한다")
        void uploadRawDataAsCsvSuccess() throws Exception {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333),
                    rawSensorDataRequest("2026-03-26T10:00:01", 11, 21, 31, 41, 0.444, 0.555, 0.666)
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            AtomicReference<String> csvContent = new AtomicReference<>();//실제 서비스는 내부에서 임시 파일 생성 후 바로 삭제
            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenAnswer(invocation -> {
                        Path csvPath = invocation.getArgument(0);
                        csvContent.set(Files.readString(csvPath)); //경로를 읽는다.
                        return invocation.getArgument(1);
                    });

            RawDataUploadResponse response = rawDataService.uploadRawDataAsCsv(userId, requests);

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class); //실제 업로드된 path
            ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class); //실제 생성된 objectKey
            verify(fileStorageService).uploadCsv(pathCaptor.capture(), objectKeyCaptor.capture());

            Path uploadedPath = pathCaptor.getValue();
            String objectKey = objectKeyCaptor.getValue();

            assertThat(response.getObjectKey()).isEqualTo(objectKey);
            assertThat(objectKey).startsWith("raw-data/user-1/").endsWith(".csv");
            assertThat(csvContent.get())
                    .contains("timestamp,p1,p2,p3,p4,acc_x,acc_y,acc_z")
                    .contains("2026-03-26T10:00:00,10,20,30,40,0.111,0.222,0.333")
                    .contains("2026-03-26T10:00:01,11,21,31,41,0.444,0.555,0.666");
            assertThat(Files.exists(uploadedPath)).isFalse(); //로컬에서 파일이 삭제되었나 확인함

            verify(rawDataMetadataService).save(user, objectKey);
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 업로드를 진행하지 않는다")
        void uploadRawDataAsCsvFailsWhenUserNotFound() {
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, List.of()))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(fileStorageService, never()).uploadCsv(any(Path.class), anyString());
            verify(rawDataMetadataService, never()).save(any(User.class), anyString());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }

        @Test
        @DisplayName("메타데이터 저장에 실패하면 업로드한 objectKey를 보상 삭제하고 로컬 임시 파일도 삭제한다")
        void uploadRawDataAsCsvCompensatesS3WhenMetadataSaveFails() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");
            List<RawSensorDataRequest> requests = List.of(
                    rawSensorDataRequest("2026-03-26T10:00:00", 10, 20, 30, 40, 0.111, 0.222, 0.333)
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(fileStorageService.uploadCsv(any(Path.class), anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            org.mockito.Mockito.doThrow(new RuntimeException("metadata save failed"))
                    .when(rawDataMetadataService).save(eq(user), anyString());

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, requests))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("metadata save failed");

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(fileStorageService).uploadCsv(pathCaptor.capture(), objectKeyCaptor.capture());

            Path uploadedPath = pathCaptor.getValue();
            String objectKey = objectKeyCaptor.getValue();

            verify(rawDataMetadataService).save(user, objectKey);
            verify(fileStorageService).deleteIfExists(objectKey);
            assertThat(Files.exists(uploadedPath)).isFalse();
        }

        @Test
        @DisplayName("센서 데이터가 비정상적이면 IllegalCsvCreateException을 던지고 업로드를 진행하지 않는다")
        void uploadRawDataAsCsvFailsWhenCsvCreationFails() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, null))
                    .isInstanceOf(IllegalCsvCreateException.class)
                    .hasMessage("CSV 파일 생성에 실패했습니다.");

            verify(fileStorageService, never()).uploadCsv(any(Path.class), anyString());
            verify(rawDataMetadataService, never()).save(any(User.class), anyString());
            verify(fileStorageService, never()).deleteIfExists(anyString());
        }
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
