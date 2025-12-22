package com.example.catchme.exception.exceptions;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message); //서버 장애가 아닌 회원가입 정책상의 실패임을 알리기 위함
    }
}
