package com.ldang03.common.redis;

/**
 * Bằng chứng sở hữu 1 lần acquire lock: {@code key} là key trên Redis, {@code token} là UUID
 * chỉ riêng lần acquire này biết. Bắt buộc phải trả lại nguyên vẹn khi unlock — nhờ token mà
 * ta phân biệt được "lock của tôi" với "lock của instance khác đã acquire sau khi lock của tôi
 * hết hạn", tránh xoá nhầm.
 */
public record LockToken(
        String key,
        String token) {
}
