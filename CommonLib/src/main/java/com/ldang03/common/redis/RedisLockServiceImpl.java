package com.ldang03.common.redis;

import glide.api.GlideClient;
import glide.api.models.Script;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.SetOptions;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockServiceImpl implements RedisLockService {

    // Compare-and-delete. Bắt buộc chạy trong Lua vì Redis chỉ bảo đảm atomic cho cả script:
    // nếu tách GET rồi DEL, giữa 2 lệnh lock của mình có thể hết hạn và instance khác acquire
    // được — lúc đó DEL sẽ xoá nhầm lock của họ.
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else "
                    + "return 0 "
                    + "end";

    private static final long COMMAND_TIMEOUT_SECONDS = 5;

    private final GlideClient glideClient;

    // Script giữ tham chiếu tới script đã cache ở lớp native của glide — tạo 1 lần rồi dùng lại,
    // tạo mới mỗi lần unlock sẽ rò rỉ. Field có initializer nên Lombok bỏ qua khi sinh constructor.
    private final Script unlockScript =
            new Script(UNLOCK_LUA, false);

    @Override
    public Optional<LockToken> tryLock(
            String key,
            Duration ttl) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(ttl);

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "ttl must be positive, got: " + ttl);
        }

        String token = UUID.randomUUID().toString();

        // SET key token NX PX ttl — 1 lệnh atomic làm cả 3 việc: NX cho đúng 1 client thắng,
        // PX bảo đảm lock tự nhả nếu process chết, value=token để unlock so chủ sở hữu.
        // Tách thành SETNX + EXPIRE sẽ deadlock vĩnh viễn nếu process chết giữa 2 lệnh.
        SetOptions options = SetOptions.builder()
                .conditionalSetOnlyIfNotExist()
                .expiry(SetOptions.Expiry.Milliseconds(ttl.toMillis()))
                .build();

        try {

            String result =
                    glideClient.set(key, token, options)
                            .orTimeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .join();

            // NX không thoả -> glide trả null (không phải "OK").
            if (!"OK".equals(result)) {
                return Optional.empty();
            }

            return Optional.of(new LockToken(key, token));

        } catch (Exception ex) {

            // Fail-closed — khác RedisService (cache) nuốt lỗi rồi chạy tiếp. Với lock, Redis lỗi
            // mà báo "acquired" là mất hoàn toàn tính đúng đắn, nên coi như không lấy được.
            log.error(
                    "Redis LOCK acquire error key={}",
                    key,
                    ex);

            return Optional.empty();
        }
    }

    @Override
    public boolean unlock(
            LockToken token) {

        Objects.requireNonNull(token);

        try {

            Object result =
                    glideClient.invokeScript(
                                    unlockScript,
                                    ScriptOptions.builder()
                                            .key(token.key())
                                            .arg(token.token())
                                            .build())
                            .orTimeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .join();

            boolean released =
                    result instanceof Long deleted
                            && deleted == 1L;

            if (!released) {
                log.warn(
                        "Redis LOCK release no-op key={} — lock đã hết hạn hoặc đang do instance"
                                + " khác giữ, cân nhắc tăng ttl",
                        token.key());
            }

            return released;

        } catch (Exception ex) {

            // Không fail-closed được ở đây: lock sẽ tự hết hạn theo TTL, chỉ cần log.
            log.error(
                    "Redis LOCK release error key={}",
                    token.key(),
                    ex);

            return false;
        }
    }

    @Override
    public boolean runWithLock(
            String key,
            Duration ttl,
            Runnable action) {

        Objects.requireNonNull(action);

        Optional<LockToken> token = tryLock(key, ttl);

        if (token.isEmpty()) {
            return false;
        }

        try {
            action.run();
        } finally {
            unlock(token.get());
        }

        return true;
    }

    @PreDestroy
    public void releaseUnlockScript() {

        try {
            unlockScript.close();
        } catch (Exception ex) {
            log.warn("Failed to close unlock script", ex);
        }
    }
}
