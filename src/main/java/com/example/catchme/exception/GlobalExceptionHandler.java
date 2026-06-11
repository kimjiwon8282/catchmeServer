package com.example.catchme.exception;

import com.example.catchme.exception.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 이메일 중복 회원가입
     * → 409 Conflict
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
            DuplicateEmailException e
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * 로그인 실패
     * → 401 Unauthorized
     */
    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLogin(
            InvalidLoginException e
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * 사용자 리소스 없음
     * → 404 Not Found
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException e
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 비밀번호 변경 시 예외 발생
     * → 401 Unauthorized
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(
            InvalidPasswordException e
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * CSV 파일 생성 실패
     * → 500 Internal Server Error
     */
    @ExceptionHandler(IllegalCsvCreateException.class)
    public ResponseEntity<Map<String, Object>> handleCsvCreateFail(
            IllegalCsvCreateException e
    ) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * CSV 파일 로컬 삭제 실패
     * → 500 Internal Server Error
     */
    @ExceptionHandler(LocalFileDeleteFailException.class)
    public ResponseEntity<Map<String, Object>> handleLocalFileDeleteFail(
            LocalFileDeleteFailException e
    ) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * S3 업로드 실패
     * → 503 Service Unavailable
     * → 프론트 재전송 대상
     */
    @ExceptionHandler(S3UploadFailException.class)
    public ResponseEntity<Map<String, Object>> handleS3UploadFail(
            S3UploadFailException e
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of(
                                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                                "error", "S3_UPLOAD_FAILED",
                                "message", "Raw 데이터 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.",
                                "retryable", true
                        )
                );
    }

    /**
     * Raw 데이터는 S3에 저장되었지만 DB 메타데이터 저장 실패
     * → 500 Internal Server Error
     */
    @ExceptionHandler(RawDataMetadataSaveFailException.class)
    public ResponseEntity<Map<String, Object>> handleRawDataMetadataSaveFail(
            RawDataMetadataSaveFailException e
    ) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    /**
     * 외부 API 호출 실패
     * → 502 Bad Gateway
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<Map<String, Object>> handleExternalApi(
            ExternalApiException e
    ) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /**
     * 비즈니스 상태 오류
     * → 409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException e
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * 잘못된 요청 값
     * → 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException e
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * @RequestBody가 없거나 JSON 형식이 잘못되었을 때
     * → 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParseError(
            HttpMessageNotReadableException e
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "요청 본문(Body)이 비어있거나 형식이 올바르지 않습니다."
        );
    }

    /**
     * @Valid 검증 실패 시
     * → 400 Bad Request
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(
            BindException e
    ) {
        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e
    ) {
        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();
        Map<String, String> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage() == null
                                ? "요청 값이 올바르지 않습니다."
                                : fieldError.getDefaultMessage(),
                        (first, second) -> first
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        Map.of(
                                "status", HttpStatus.BAD_REQUEST.value(),
                                "error", HttpStatus.BAD_REQUEST.name(),
                                "message", errorMessage,
                                "fields", fieldErrors
                        )
                );
    }

    /**
     * 그 외 예측하지 못한 예외
     * → 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception e
    ) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다."
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(
                        Map.of(
                                "status", status.value(),
                                "error", status.name(),
                                "message", message
                        )
                );
    }
}
