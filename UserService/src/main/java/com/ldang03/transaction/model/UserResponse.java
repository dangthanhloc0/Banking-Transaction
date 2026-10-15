package com.ldang03.transaction.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID userId;

    private String email;

    private String fullName;

    private String avatarUrl;

    private LocalDateTime createdAt;

}
