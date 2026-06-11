package com.example.catchme.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fields
) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null);
    }

    public static ErrorResponse withFields(
            int status,
            String error,
            String message,
            Map<String, String> fields
    ) {
        return new ErrorResponse(status, error, message, fields);
    }
}
