package com.ldang03.common.response;

import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public class customResponse {

    public static <T> ResponseEntity<com.ldang03.common.response.ApiResponse<T>> responseSuccess(int statusCode,Boolean status, String message, T data) {
        return ResponseEntity.status(statusCode).body(
                com.ldang03.common.response.ApiResponse.<T>builder()
                        .status(status)
                        .message(message)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    public static ResponseEntity<ApiResponse<Object>> exceptionResponse(int statusCode, Boolean status, String message) {
        return ResponseEntity.status(statusCode).body(
                ApiResponse.builder()
                        .status(status)
                        .message(message)
                        .data(null)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
