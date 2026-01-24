package com.example.catchme.exception;

import com.example.catchme.exception.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

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
    //비밀번호 변경 시 예외 발생
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(
            InvalidPasswordException e
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
    //cvs 파일 생성 시 예외 발생
    @ExceptionHandler(IllegalCsvCreateException.class)
    public ResponseEntity<Map<String, Object>> handleCsvCreateFail(
            IllegalCsvCreateException e
    ) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage());
    }
    //csv파일 로컬 삭제 실패
    @ExceptionHandler(LocalFileDeleteFailException.class)
    public ResponseEntity<Map<String, Object>> handleLocalFileDeleteFail(
            LocalFileDeleteFailException e
    ){
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage());
    }

    @ExceptionHandler(S3UploadFailException.class)
    public ResponseEntity<Map<String, Object>> handleS3UploadFail(
            S3UploadFailException e
    ){
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage());
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

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<Map<String, Object>> handleExternalApi(
            ExternalApiException e
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY,
                e.getMessage()
        );
    }

    /**
     * @RequestBody가 없거나 JSON 형식이 잘못되었을 때
     * → 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJsonParseError(
            HttpMessageNotReadableException e
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "요청 본문(Body)이 비어있거나 형식이 올바르지 않습니다.");
    }

    /**
     * @Valid 검증 실패 시 (@ModelAttribute)
     * → 400 Bad Request 및 에러 메시지 반환
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e) {
        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0) // 첫 번째 에러 메시지만 보여줌
                .getDefaultMessage();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    /* =========================
       공통 에러 응답 생성 메서드
       ========================= */
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
