package com.example.catchme.exception.exceptions;

public class QrServiceUnavailableException extends RuntimeException {

    public QrServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
