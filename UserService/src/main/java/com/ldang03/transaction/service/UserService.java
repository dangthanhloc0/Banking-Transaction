package com.ldang03.transaction.service;

import com.ldang03.transaction.model.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse getById(UUID userId);

    List<UserResponse> getAll();

    Boolean checkUserExist(String userId);
}
