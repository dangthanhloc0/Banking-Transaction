package com.ldang03.common.redis;

import java.time.Duration;
import java.util.Optional;

/**
 * Distributed lock trên Redis/Valkey cho các đoạn code "chỉ 1 instance được chạy".
 *
 * <p>Đây là lock single-node (1 {@link glide.api.GlideClient} tới 1 node) nên mutual exclusion
 * là best-effort: nếu node failover sang replica chưa replicate kịp, 2 instance vẫn có thể cùng
 * giữ lock. Critical section vì vậy nên idempotent.
 *
 * <p>Không có cơ chế gia hạn — TTL phải dài hơn thời gian chạy tối đa của critical section.
 */
public interface RedisLockService {

    /**
     * Thử acquire bằng {@code SET key token NX PX ttl}.
     *
     * @return token để unlock; {@link Optional#empty()} nếu đã có instance khác giữ lock
     *         <b>hoặc</b> Redis lỗi (fail-closed — không lấy được thì đừng chạy).
     * @throws IllegalArgumentException nếu {@code ttl} không dương
     */
    Optional<LockToken> tryLock(
            String key,
            Duration ttl);

    /**
     * Nhả lock bằng compare-and-delete (Lua): chỉ xoá khi value còn đúng bằng token.
     *
     * @return true nếu đã nhả đúng lock của mình; false nếu lock đã hết hạn, đang do instance
     *         khác giữ, hoặc Redis lỗi.
     */
    boolean unlock(
            LockToken token);

    /**
     * Cách dùng mặc định: acquire → chạy {@code action} → unlock trong {@code finally}.
     * Exception từ {@code action} vẫn được ném lên caller, lock vẫn được nhả.
     *
     * @return true nếu lấy được lock và đã chạy {@code action}; false nếu bỏ lượt.
     */
    boolean runWithLock(
            String key,
            Duration ttl,
            Runnable action);
}
