package com.ldang03.common.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiResponse<T> {

    private Boolean status;

    private String message;

    private T data;

    private LocalDateTime timestamp;
}
