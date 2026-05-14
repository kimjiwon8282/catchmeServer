package com.example.catchme.exception.exceptions;

public class RawDataMetadataSaveFailException extends RuntimeException {

    public RawDataMetadataSaveFailException(String message) {
        super(message);
    }

    public RawDataMetadataSaveFailException(String message, Throwable cause) {
        super(message, cause);
    }
}