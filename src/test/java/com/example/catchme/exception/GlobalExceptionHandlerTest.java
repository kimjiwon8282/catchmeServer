package com.example.catchme.exception;

import com.example.catchme.exception.exceptions.S3UploadFailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler =
            new GlobalExceptionHandler();

    @Test
    @DisplayName("S3 업로드 실패 시 503과 retryable=true를 응답한다")
    void handleS3UploadFail() {
        S3UploadFailException exception = new S3UploadFailException(
                "Raw 데이터 S3 업로드에 실패했습니다.",
                new RuntimeException("s3 upload failed")
        );

        ResponseEntity<Map<String, Object>> response =
                globalExceptionHandler.handleS3UploadFail(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        Map<String, Object> body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(503);
        assertThat(body.get("error")).isEqualTo("S3_UPLOAD_FAILED");
        assertThat(body.get("message"))
                .isEqualTo("Raw 데이터 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.");
        assertThat(body.get("retryable")).isEqualTo(true);
    }
}