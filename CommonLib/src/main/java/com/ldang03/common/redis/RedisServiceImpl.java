package com.ldang03.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import glide.api.GlideClient;
import glide.api.models.commands.SetOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisServiceImpl implements RedisService {

    private final GlideClient glideClient;

    private final ObjectMapper objectMapper;

    @Override
    public <T> void set(
            String key,
            T value,
            Duration ttl) {

        Objects.requireNonNull(key);

        try {

            String json =
                    objectMapper.writeValueAsString(value);

            // ttl null/không dương = cố ý set vĩnh viễn; còn lại phải kèm PX, không thì
            // mọi cache key sống mãi và TTL caller truyền vào bị bỏ qua trong im lặng.
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {

                glideClient.set(key, json)
                        .orTimeout(5, TimeUnit.SECONDS)
                        .join();

            } else {

                SetOptions options = SetOptions.builder()
                        .expiry(SetOptions.Expiry.Milliseconds(ttl.toMillis()))
                        .build();

                glideClient.set(key, json, options)
                        .orTimeout(5, TimeUnit.SECONDS)
                        .join();
            }

        } catch (Exception ex) {

            log.error(
                    "Redis SET error key={}",
                    key,
                    ex);
        }
    }

    @Override
    public <T> Optional<T> get(
            String key,
            TypeReference<T> typeReference) {

        Objects.requireNonNull(key);

        try {

            String json =
                    glideClient.get(key)
                            .orTimeout(5, TimeUnit.SECONDS)
                            .join();

            if (json == null) {
                return Optional.empty();
            }

            T value =
                    objectMapper.readValue(
                            json,
                            typeReference);

            return Optional.of(value);

        } catch (Exception ex) {

            log.error(
                    "Redis GET error key={}",
                    key,
                    ex);

            return Optional.empty();
        }
    }

    @Override
    public void delete(
            String key) {

        Objects.requireNonNull(key);

        try {

            glideClient.del(new String[]{key})
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();

        } catch (Exception ex) {

            log.error(
                    "Redis DELETE error key={}",
                    key,
                    ex);
        }
    }

    @Override
    public boolean exists(
            String key) {

        Objects.requireNonNull(key);

        try {

            Long count =
                    glideClient.exists(new String[]{key})
                            .orTimeout(5, TimeUnit.SECONDS)
                            .join();

            return count != null
                    && count > 0;

        } catch (Exception ex) {

            log.error(
                    "Redis EXISTS error key={}",
                    key,
                    ex);

            return false;
        }
    }
}
