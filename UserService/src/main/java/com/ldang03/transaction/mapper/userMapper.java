package com.ldang03.transaction.mapper;

import com.ldang03.transaction.entity.User;
import com.ldang03.transaction.model.UserResponse;

public class userMapper {

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getUserCreatedAt())
                .build();
    }
}
