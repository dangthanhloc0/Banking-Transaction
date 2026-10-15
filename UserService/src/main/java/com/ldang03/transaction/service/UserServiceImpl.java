package com.ldang03.transaction.service;

import com.ldang03.transaction.mapper.userMapper;
import com.ldang03.transaction.model.UserResponse;
import com.ldang03.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private Logger log = org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    @Override
    public UserResponse getById(UUID userId) {
        return userRepository.findByUserIdAndUserDeletedAtIsNull(userId)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new com.ldang03.common.exception.NotFoundException("User not found"));
    }

    @Override
    public List<UserResponse> getAll() {
        return userRepository.findAllByUserDeletedAtIsNull()
                .stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    @Override
    public Boolean checkUserExist(String userId) {
        Boolean exists = userRepository.findByUserIdAndUserDeletedAtIsNull(UUID.fromString(userId)).isPresent();
        if(exists) {
            log.info("User with userId {} found", userId);
        } else {
            log.info("User with userId {} not found", userId);
        }
        return exists;
    }


}
