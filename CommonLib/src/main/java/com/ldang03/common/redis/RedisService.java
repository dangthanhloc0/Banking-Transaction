package com.ldang03.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Optional;

public interface RedisService {

    <T> void set(
            String key,
            T value,
            Duration ttl);

    <T> Optional<T> get(
            String key,
            TypeReference<T> typeReference);

    void delete(
            String key);

    boolean exists(
            String key);
}
