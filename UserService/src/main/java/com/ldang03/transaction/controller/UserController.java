package com.ldang03.transaction.controller;

import com.ldang03.common.response.ApiResponse;
import com.ldang03.common.response.customResponse;
import com.ldang03.transaction.model.UserResponse;
import com.ldang03.transaction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @GetMapping("/v1/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        return customResponse.responseSuccess(200, true,"User found successfully", userService.getById(id));
    }

    @PostMapping("/v1/checkUserExist")
    public ResponseEntity<ApiResponse<Boolean>> checkUserExist(@RequestBody String userId) {
        return customResponse.responseSuccess(200, true,"User Existed ", userService.checkUserExist(userId));
    }
}

