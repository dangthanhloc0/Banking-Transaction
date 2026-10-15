# Banking Transaction – Hệ thống chuyển tiền giữa các tài khoản

Hệ thống chuyển tiền dựa trên microservices (Java 17, Spring Boot 3.3), xử lý bất đồng bộ qua Kafka theo mô hình **saga + transactional outbox**, có **idempotency**, khóa dòng theo thứ tự cố định để tránh deadlock, và truy vết toàn bộ luồng bằng **traceId** (Micrometer Tracing → Elasticsearch → Kibana).

Đây là dự án học tập/thực hành. Trọng tâm là tính đúng đắn của tiền (không mất, không sinh thêm, không trừ hai lần) khi có retry, tin nhắn trùng và nhiều instance chạy song song.

## Mục lục
- [Kiến trúc](#kiến-trúc)
- [Các service](#các-service)
- [Luồng chuyển tiền](#luồng-chuyển-tiền)
- [Các quyết định thiết kế chính](#các-quyết-định-thiết-kế-chính)
- [Công nghệ](#công-nghệ)
- [Chạy bằng Docker Compose](#chạy-bằng-docker-compose)
- [Chạy từ IDE](#chạy-từ-ide)
- [Gọi API](#gọi-api)
- [Kafka topics](#kafka-topics)
- [Truy vết log với Kibana](#truy-vết-log-với-kibana)
- [Kiểm thử tải](#kiểm-thử-tải)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Hạn chế và việc cần làm](#hạn-chế-và-việc-cần-làm)

## Kiến trúc

```
                         HTTP POST (Idempotency-Key)
   Client ───────────────────────────────▶ TransactionService (:2000 / :2001)
                                              │  1. kiểm tra user  ──HTTP──▶ UserService (:1000)
                                              │  2. 1 DB transaction ngắn: lưu Transaction(PENDING) + OutboxEvent
                                              ▼
                                         MySQL: transaction.*
                                              │
                                              │  3. relay (@Scheduled) đọc outbox, chiếm lô bằng
                                              │     SELECT … FOR UPDATE SKIP LOCKED + lease
                                              ▼
                       Kafka: payment.transfer.requested.v1
                                              │
                                              ▼
                         AccountService (:3000 / :3001)
                           4. idempotency ledger + khóa 2 tài khoản + cộng/trừ số dư
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
      payment.transfer.completed.v1                       payment.transfer.failed.v1
                    └─────────────────────────┬─────────────────────────┘
                                              ▼
                          TransactionService: cập nhật Transaction → SUCCESS / FAILED
```

Log của cả 3 service (JSON, có `traceId`) → Filebeat → Elasticsearch → Kibana.

## Các service

| Service | Cổng | Vai trò | Database |
|---|---|---|---|
| `UserService` | 1000 | Thông tin người dùng, kiểm tra user tồn tại | `user` |
| `TransactionService` | 2000 (instance 1), 2001 (instance 2) | Nhận yêu cầu chuyển tiền, lưu giao dịch + outbox, relay lên Kafka, nhận kết quả | `transaction` |
| `AccountService` | 3000 (instance 1), 3001 (instance 2) | Tài khoản, số dư, sổ ledger, thực hiện chuyển tiền | `account` |
| `CommonLib` | – | Thư viện dùng chung: Kafka (envelope JSON, publisher, config), trace (`TraceSupport`), exception, HTTP client, sinh UUID v7 | – |

Hạ tầng (docker-compose): MySQL 8, Kafka 3.7 (KRaft, 1 broker), Kafka UI, Elasticsearch 8.15, Kibana, Filebeat.

## Luồng chuyển tiền

1. Client gọi `POST /transactions/v1/transactions` kèm header `Idempotency-Key` (UUID).
2. `TransactionService` kiểm tra khóa idempotency; nếu đã tồn tại thì trả lại `transactionId` cũ, không xử lý lại.
3. Gọi `UserService` để kiểm tra user (ngoài DB transaction).
4. Trong **một transaction ngắn**, lưu `Transaction` (trạng thái `PENDING`) và `OutboxEvent` (payload là envelope JSON có `traceId`). Trả về `202 Accepted`.
5. Relay đọc outbox theo lô, gửi lên topic `payment.transfer.requested.v1`, rồi đánh dấu `PUBLISHED`.
6. `AccountService` nhận message:
   - tạo bản ghi ledger với `idempotency_key = transactionId` (unique) để chống xử lý trùng;
   - khóa hai tài khoản theo **thứ tự số tài khoản tăng dần**, kiểm tra tồn tại / đang hoạt động / đủ tiền, rồi trừ và cộng;
   - gửi `completed` hoặc `failed` (lỗi nghiệp vụ được báo lại, không retry).
7. `TransactionService` nhận kết quả và cập nhật trạng thái bằng compare-and-set (`PENDING → SUCCESS | FAILED`).

Trạng thái giao dịch: `PENDING → SUCCESS | FAILED`.

## Các quyết định thiết kế chính

- **Transactional outbox**: ghi giao dịch và sự kiện trong cùng một DB transaction, relay đẩy lên Kafka. Không có dual-write giữa MySQL và Kafka.
- **At-least-once + idempotent consumer**: relay có thể gửi trùng (crash, lease hết hạn), nên `AccountService` chống trùng bằng unique `idempotency_key` trong ledger.
- **Chiếm lô outbox an toàn cho nhiều instance**: `SELECT … FOR UPDATE SKIP LOCKED` và cập nhật `PROCESSING` + `next_retry_at` (lease) nằm trong cùng một transaction; lô bị bỏ dở sẽ được nhặt lại sau khi lease hết hạn.
- **Idempotency-Key ở API**: client gửi UUID cho mỗi ý định chuyển tiền; khóa lưu trong `transactions` với unique constraint, xử lý cả trường hợp hai request trùng đến cùng lúc (`DataIntegrityViolationException`).
- **Tiền**: `BigDecimal` / cột `DECIMAL`, không dùng `double`.
- **Chống deadlock**: luôn khóa tài khoản có số nhỏ hơn trước (`compareTo`), dùng pessimistic lock (`FOR UPDATE`); các exception nghiệp vụ không rollback (`noRollbackFor`) để ghi được trạng thái FAILED của ledger.
- **Retry và dead letter**: `@RetryableTopic` (3 lần, backoff 1s → 2s) với các topic `-retry-1000`, `-retry-2000` và `.DLT`; lỗi parse JSON không retry.
- **Quan sát**: Micrometer Tracing (brave) với `traceId` đi qua HTTP, Kafka header và cả outbox (`TraceSupport` lưu trace context vào cột `trace_context`), nên một giao dịch đi qua nhiều service và nhiều instance vẫn có một `traceId`. Log JSON có trường `app` và `instance`.
- **Index**: unique `accounts.account_number` (khóa dòng theo số tài khoản), index `(status, next_retry_at, created_at)` cho truy vấn của relay.

## Công nghệ

Java 17 · Spring Boot 3.3.6 (Spring Data JPA/Hibernate, Spring Kafka) · MySQL 8 · Apache Kafka (KRaft) · Micrometer Tracing · Logback + logstash-logback-encoder · Filebeat / Elasticsearch / Kibana 8.15 · Docker & Docker Compose · Maven.

## Chạy bằng Docker Compose

Yêu cầu: Docker Desktop (có BuildKit), tối thiểu khoảng 6 GB RAM trống cho Docker (5 JVM, MySQL, Kafka, Elasticsearch, Kibana).

```bash
# 1. Build image và khởi động toàn bộ hệ thống
docker compose up -d --build

# 2. Chờ các service lên (lần đầu Hibernate sẽ tự tạo bảng và index)
docker compose ps

# 3. Nạp dữ liệu thử: 10.000 user và 10.000 tài khoản (mỗi tài khoản 1.000.000)
docker exec -i pm-mysql mysql -uroot -proot123 user    < UserService/docs/seed_10k_users.sql
docker exec -i pm-mysql mysql -uroot -proot123 account < AccountService/docs/seed_10k_accounts.sql
```

Lưu ý:
- Mật khẩu root MySQL mặc định `root123` (có thể ghi đè bằng biến `MYSQL_ROOT_PASSWORD`). Chỉ dùng cho môi trường phát triển.
- Cổng `3306` phải trống. Nếu đang có MySQL khác trên máy, hãy dừng nó hoặc đổi cổng trong `docker-compose.yml`.
- Có sẵn bộ seed nhỏ: `UserService/docs/seed_test_users.sql` và `AccountService/docs/seed_test_accounts.sql` (10 user, 10 tài khoản).
- Mỗi instance ghi log vào `./logs/<tên-service>-<số>/application.log`.
- Có thể tăng hiệu năng cho môi trường thử nghiệm bằng cách bỏ comment `--innodb-flush-log-at-trx-commit=2` trong compose (đánh đổi độ bền dữ liệu, **không** dùng cho môi trường thật).

| Thành phần | Địa chỉ |
|---|---|
| UserService | http://localhost:1000 |
| TransactionService | http://localhost:2000 và http://localhost:2001 |
| AccountService | http://localhost:3000 và http://localhost:3001 |
| Kafka UI | http://localhost:8080 |
| Kibana | http://localhost:5601 |
| Elasticsearch | http://localhost:9200 |

## Chạy từ IDE

1. Chỉ khởi động hạ tầng: `docker compose up -d mysql kafka kafka-init kafka-ui elasticsearch kibana filebeat`.
2. Build thư viện dùng chung: `mvn -f CommonLib/pom.xml install -DskipTests`.
3. Chạy `MainApplication` của từng service. Các service mặc định kết nối `localhost` (MySQL `3306`, Kafka `9092`), nên không cần cấu hình thêm.
4. Khi chạy trong Docker, các biến môi trường (`SPRING_DATASOURCE_URL`, `COMMON_KAFKA_BOOTSTRAP_SERVERS`, `URL_USER`, `INSTANCE_ID`...) sẽ ghi đè cấu hình trong `application.properties`.

Chạy từ IDE và Docker cùng lúc thì các cổng 1000, 2000, 3000 sẽ trùng nhau; hãy dừng một bên.

## Gọi API

Tạo giao dịch chuyển tiền (`202 Accepted`, body chứa `transactionId`):

```bash
curl -i -X POST http://localhost:2000/transactions/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
        "userId": "01900000-0000-7000-8000-000000000001",
        "debtorAccount": "1000000001",
        "creditorAccount": "1000000002",
        "amount": 1000
      }'
```

- Gửi lại **cùng** `Idempotency-Key` sẽ nhận lại cùng `transactionId` và không tạo giao dịch mới.
- `amount` phải lớn hơn 0; tài khoản gửi và nhận phải khác nhau.
- Lỗi nghiệp vụ (không đủ tiền, tài khoản không tồn tại hoặc không hoạt động) được ghi nhận bất đồng bộ: giao dịch chuyển sang `FAILED` với `failed_reason`.

Xem trạng thái giao dịch (chưa có API truy vấn, xem trực tiếp trong DB):

```sql
SELECT BIN_TO_UUID(transaction_id), transaction_status, failed_reason
FROM transaction.transactions WHERE idempotency_key = '<Idempotency-Key>';
```

Các API nội bộ khác: `UserService` (`GET /users/v1/{id}`, `POST /users/v1/checkUserExist`), `AccountService` (`POST /accounts/v1/getAccount`, `/checkAccountExist`, `/transferMoney`).

## Kafka topics

Tên topic theo dạng `payment.<domain>.<event>.v1`, mỗi topic 6 partition (khóa theo `transactionId`).

| Topic | Producer | Consumer |
|---|---|---|
| `payment.transfer.requested.v1` | TransactionService (relay) | AccountService |
| `payment.transfer.completed.v1` | AccountService | TransactionService |
| `payment.transfer.failed.v1` | AccountService | TransactionService |

Mỗi topic có thêm `-retry-1000`, `-retry-2000` và `.DLT`. Dead letter topic phải có số partition không ít hơn topic gốc.

Message là JSON envelope: `{eventId, eventType, source, occurredAt, traceId, payload}`. `concurrency` của listener được chọn sao cho tổng số luồng consumer của các instance bằng số partition (hai instance × 3 luồng = 6 partition).

## Truy vết log với Kibana

1. Mở http://localhost:5601 → tạo Data View `payment-logs-*` (trường thời gian `@timestamp`).
2. Lọc theo một giao dịch: `traceId : "<traceId>"`, sắp xếp theo `@timestamp`.
3. Lọc theo instance: `instance : "account-service-2"`; có thể chia theo `app` và `instance` trong dashboard.

Trường `traceId` của giao dịch có trong cột `trace_context` của bảng `outbox_events` và trong envelope của message Kafka.

## Kiểm thử tải

Công cụ chạy tay (không phải JUnit) tại `TransactionService/src/test/java/com/ldang03/transaction/loadtest/TestTransaction.java`, chỉ dùng thư viện chuẩn của JDK:

```
TestTransaction [total] [threads] [amount] [users] [rounds] [ports]
# ví dụ: 1000 giao dịch, 50 luồng, tag amount=2001, 10.000 user, 1 vòng, chia đều hai instance
TestTransaction 1000 50 2001 10000 1 2000,2001
```

- `amount` dùng làm "nhãn" cho lần chạy: `SELECT … FROM transactions WHERE amount = 2001` tách riêng các giao dịch của lần đó.
- Request thứ `i` được gửi tới cổng `ports[i % n]`, nên các instance nhận số request bằng nhau; kết quả in ra `202 per port`.
- Sau khi chạy, kiểm tra tính nhất quán: mọi giao dịch ở trạng thái `SUCCESS`, tổng số dư toàn hệ thống không đổi, không có message mới ở `.DLT`, và `outbox_events` không còn dòng `PROCESSING`.

```sql
SELECT status, COUNT(*) FROM transaction.outbox_events GROUP BY status;
SELECT transaction_status, COUNT(*) FROM transaction.transactions WHERE amount = 2001 GROUP BY transaction_status;
SELECT SUM(account_balance) FROM account.accounts;   -- 10.000.000.000 với bộ seed 10k
```

## Cấu trúc thư mục

```
.
├── CommonLib/            # thư viện dùng chung (Kafka, trace, exception, HTTP, UUID v7)
├── UserService/          # service người dùng
├── AccountService/       # service tài khoản + ledger
├── TransactionService/   # service giao dịch + outbox relay + công cụ test tải
├── infra/
│   ├── docker/Dockerfile # Dockerfile dùng chung cho 3 service (ARG SERVICE)
│   ├── filebeat/         # cấu hình Filebeat
│   └── mysql/init/       # tạo các database lần đầu
├── docker-compose.yml
└── CLAUDE.md             # quy ước của dự án
```

## Hạn chế và việc cần làm

- Chưa có API tra cứu trạng thái giao dịch (`GET /transactions/{id}`).
- Chưa băm nội dung request: cùng `Idempotency-Key` nhưng khác số tiền hoặc tài khoản vẫn trả lại giao dịch cũ thay vì lỗi `422`.
- Khóa idempotency là duy nhất toàn hệ thống, chưa theo cặp `(userId, key)`.
- `AccountService` chưa kiểm tra tài khoản gửi có thuộc `userId` của request hay không.
- Chưa có job đối soát cho các giao dịch kẹt ở `PENDING`, và relay chưa phân biệt lỗi hạ tầng để retry.
- `UserService` chưa truyền `traceId` (log của service này chưa nối vào cùng trace).
- Cấu hình dùng schema tự tạo bằng Hibernate (`ddl-auto=update`) và mật khẩu mặc định cho môi trường phát triển; môi trường thật cần Flyway và quản lý bí mật qua biến môi trường hoặc vault.
- Elasticsearch chạy không bật bảo mật (chỉ dành cho phát triển).
