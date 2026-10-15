# Payment Project – Instructions

## Tech stack
- Java 21, Spring Boot 3.x (Spring Framework 6, Jakarta EE – dùng `jakarta.*`, KHÔNG dùng `javax.*`)
- MySQL 8 (Spring Data JPA/Hibernate hoặc JdbcTemplate), Flyway cho migration
- Redis (Spring Data Redis / Lettuce): cache, distributed lock, idempotency key, rate limit
- Kafka (Spring Kafka): event bus giữa các service
- Build: Maven hoặc Gradle (theo file có sẵn trong repo)

## Quy tắc code Java 21
- Dùng `record` cho DTO, event, request/response.
- Dùng `sealed interface` + pattern matching `switch` cho trạng thái/kết quả thanh toán.
- Dùng virtual threads (`spring.threads.virtual.enabled=true`) cho I/O; tránh `synchronized` quanh I/O blocking (dùng `ReentrantLock`).
- Dùng `Optional`, `var` hợp lý; không trả `null` từ service.
- Constructor injection (Lombok `@RequiredArgsConstructor` nếu dự án đã dùng), không field injection.

## Kiến trúc
- Phân tầng: `controller -> service -> repository`; logic nghiệp vụ nằm ở service/domain, controller chỉ validate + map.
- Package theo domain: `payment`, `wallet`, `transaction`, `notification`... mỗi package có `api / service / domain / infra`.
- Không expose entity ra API; luôn map sang DTO.
- Exception nghiệp vụ riêng (`PaymentException` + error code), xử lý tập trung bằng `@RestControllerAdvice`.

## Quy tắc thanh toán (QUAN TRỌNG)
- **Tiền**: luôn dùng `BigDecimal` (hoặc lưu số nguyên minor unit `long`), KHÔNG dùng `double/float`. Kèm `currency`. Cột MySQL: `DECIMAL(19,4)`.
- **Idempotency**: mọi API tạo/trừ tiền nhận header `Idempotency-Key`; lưu key vào Redis (`SET NX EX`) + unique constraint trong MySQL để chống xử lý trùng.
- **Transaction**: `@Transactional` ở service; giữ transaction ngắn, không gọi HTTP/Kafka bên ngoài trong transaction dài.
- **Concurrency**: cập nhật số dư dùng optimistic lock (`@Version`) hoặc `SELECT ... FOR UPDATE`; không check-then-update không khoá.
- **State machine**: `CREATED -> PENDING -> SUCCESS | FAILED | CANCELLED | REFUNDED`; chỉ cho phép chuyển trạng thái hợp lệ.
- **Audit**: bảng ledger/transaction chỉ append, không sửa/xoá; có `created_at`, `updated_at`, `trace_id`.
- Không log số thẻ, CVV, token, PII; mask dữ liệu nhạy cảm.

## MySQL
- Schema thay đổi qua Flyway (`V{n}__desc.sql`), không dùng `ddl-auto=update` ở môi trường thật.
- Index cho cột tra cứu (`order_id`, `idempotency_key`, `user_id`, `status`, `created_at`); unique cho khoá nghiệp vụ.
- Engine InnoDB, charset `utf8mb4`. Tránh N+1 (dùng `join fetch`/`EntityGraph`), phân trang cho list.

## Redis
- Key có prefix và TTL: `payment:idem:{key}`, `payment:lock:{orderId}`, `payment:cache:{id}`.
- Distributed lock: `SET key value NX PX ttl` (hoặc Redisson), unlock bằng Lua kiểm tra value.
- Redis chỉ là cache/lock, **nguồn sự thật là MySQL**. Cache phải chịu được mất dữ liệu.
- Serialize JSON (không dùng JDK serialization).

## Kafka
- Topic đặt tên: `payment.<domain>.<event>.v1` (vd `payment.transaction.completed.v1`).
- Event là `record` có `eventId`, `occurredAt`, `payload`, `traceId`; có versioning schema.
- Producer: `acks=all`, `enable.idempotence=true`; key theo `orderId`/`accountId` để giữ thứ tự.
- **Transactional Outbox**: ghi event vào bảng `outbox` cùng transaction MySQL, relay/CDC đẩy lên Kafka (không dual-write).
- Consumer: idempotent (check `eventId` đã xử lý), `enable.auto.commit=false`, retry có backoff + Dead Letter Topic (`.DLT`).
- Không xử lý nặng/blocking lâu trong listener; cấu hình `max.poll.interval.ms` phù hợp.

## Test
- JUnit 5 + Mockito cho unit test; Testcontainers (MySQL, Redis, Kafka) cho integration test.
- Bắt buộc test: idempotency, race condition cập nhật số dư, retry/DLT, chuyển trạng thái sai.
- Tên test: `should_<expected>_when_<condition>`.

## Cấu hình & bảo mật
- Secret (DB password, Redis, Kafka) lấy từ env/vault, không hardcode, không commit.
- Profile: `application-local|dev|prod.yml`.
- Validate input bằng Jakarta Validation (`@Valid`, `@NotNull`, `@Positive`...).
- Có timeout cho mọi kết nối ngoài (DB, Redis, Kafka, HTTP client) và observability: Actuator, Micrometer, log có `traceId`.

## Cách làm việc với Claude
- Trả lời bằng tiếng Việt, code/comment/tên biến bằng tiếng Anh.
- Sửa tối thiểu, đúng phạm vi yêu cầu; theo style code hiện có.
- Với thay đổi liên quan tiền/số dư/DB schema: nêu rõ rủi ro và cách test trước khi áp dụng.
- Không tự chạy lệnh phá huỷ (drop table, xoá topic, flush Redis) khi chưa hỏi.
