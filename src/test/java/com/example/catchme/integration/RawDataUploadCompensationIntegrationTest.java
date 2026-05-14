package com.example.catchme.integration;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.exception.exceptions.IllegalCsvCreateException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.impl.rawData.RawDataServiceImpl;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Raw data 업로드에서
 * 1) 로컬 CSV 생성
 * 2) S3 업로드
 * 3) DB 메타데이터 저장
 * 4) 실패 시 S3 보상 삭제
 * 5) finally 에서 로컬 파일 삭제
 * 가 실제로 이어지는지 검증한다.
 */
@SpringJUnitConfig(classes = {
        RawDataServiceImpl.class,
        RawDataUploadCompensationIntegrationTest.MockConfig.class
})
class RawDataUploadCompensationIntegrationTest {

    @Autowired
    private RawDataServiceImpl rawDataService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private RawDataMetadataService rawDataMetadataService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        reset(fileStorageService, rawDataMetadataService, userRepository);
    }

    @Test
    @DisplayName("업로드가 성공하면 objectKey를 반환하고 로컬 CSV는 삭제된다")
    void uploadRawDataAsCsv_shouldUploadAndDeleteLocalFile_whenSuccess() throws Exception {
        Long userId = 1L;
        User user = user(userId, "user@catchme.com", "encoded", "지원", Role.USER);
        List<RawSensorDataRequest> requests = List.of(sampleRequest());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);

        RawDataUploadResponse response = rawDataService.uploadRawDataAsCsv(userId, requests);

        verify(fileStorageService).uploadCsv(pathCaptor.capture(), objectKeyCaptor.capture());
        verify(rawDataMetadataService).save(eq(user), eq(objectKeyCaptor.getValue()));
        verify(fileStorageService, never()).deleteIfExists(anyString());

        Path uploadedCsvPath = pathCaptor.getValue();
        assertThat(Files.exists(uploadedCsvPath)).isFalse();

        String objectKey = objectKeyCaptor.getValue();
        assertThat(response.getObjectKey()).isEqualTo(objectKey);
        assertThat(objectKey).startsWith("raw-data/user-1/");
        assertThat(objectKey).endsWith(".csv");
    }

    @Test
    @DisplayName("메타데이터 저장이 실패하면 S3 객체를 보상 삭제하고 로컬 CSV도 삭제된다")
    void uploadRawDataAsCsv_shouldCompensateS3AndDeleteLocalFile_whenMetadataSaveFails() throws Exception {
        Long userId = 1L;
        User user = user(userId, "user@catchme.com", "encoded", "지원", Role.USER);
        List<RawSensorDataRequest> requests = List.of(sampleRequest());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);

        when(fileStorageService.uploadCsv(pathCaptor.capture(), objectKeyCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        doThrow(new IllegalStateException("DB 메타데이터 저장 실패"))
                .when(rawDataMetadataService).save(any(User.class), anyString());

        assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, requests))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 메타데이터 저장 실패");

        verify(fileStorageService).uploadCsv(any(Path.class), anyString());
        verify(rawDataMetadataService).save(eq(user), eq(objectKeyCaptor.getValue()));
        verify(fileStorageService).deleteIfExists(objectKeyCaptor.getValue());

        Path uploadedCsvPath = pathCaptor.getValue();
        assertThat(Files.exists(uploadedCsvPath)).isFalse();
    }

    @Test
    @DisplayName("CSV 생성 자체가 실패하면 S3 보상 삭제는 하지 않는다")
    void uploadRawDataAsCsv_shouldNotDeleteS3_whenCsvCreationFails() {
        Long userId = 1L;
        User user = user(userId, "user@catchme.com", "encoded", "지원", Role.USER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RawSensorDataRequest bad = mock(RawSensorDataRequest.class);
        when(bad.getTimestamp()).thenThrow(new RuntimeException("CSV write fail"));

        assertThatThrownBy(() -> rawDataService.uploadRawDataAsCsv(userId, List.of(bad)))
                .isInstanceOf(IllegalCsvCreateException.class)
                .hasMessage("CSV 파일 생성에 실패했습니다.");

        verify(fileStorageService, never()).uploadCsv(any(Path.class), anyString());
        verify(rawDataMetadataService, never()).save(any(User.class), anyString());
        verify(fileStorageService, never()).deleteIfExists(anyString());
    }

    private User user(Long id, String email, String password, String name, Role role) {
        User user = User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RawSensorDataRequest sampleRequest() {
        RawSensorDataRequest request = new RawSensorDataRequest();
        ReflectionTestUtils.setField(request, "timestamp", "2026-03-29T12:00:00");
        ReflectionTestUtils.setField(request, "pressure1", 10);
        ReflectionTestUtils.setField(request, "pressure2", 20);
        ReflectionTestUtils.setField(request, "pressure3", 30);
        ReflectionTestUtils.setField(request, "pressure4", 40);
        ReflectionTestUtils.setField(request, "accX", 0.1);
        ReflectionTestUtils.setField(request, "accY", 0.2);
        ReflectionTestUtils.setField(request, "accZ", 0.3);
        return request;
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        FileStorageService fileStorageService() {
            return mock(FileStorageService.class);
        }

        @Bean
        RawDataMetadataService rawDataMetadataService() {
            return mock(RawDataMetadataService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
