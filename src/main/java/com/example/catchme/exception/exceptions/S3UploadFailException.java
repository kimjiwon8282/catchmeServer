package com.example.catchme.exception.exceptions;

public class S3UploadFailException extends RuntimeException {

    public S3UploadFailException(String message) {
        super(message);
    }
}
