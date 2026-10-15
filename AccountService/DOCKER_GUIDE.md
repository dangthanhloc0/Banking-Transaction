# Hướng dẫn Docker cho Spring Boot Backend

## Mục lục
1. [Tổng quan project](#1-tổng-quan-project)
2. [Dockerfile giải thích từng dòng](#2-dockerfile-giải-thích-từng-dòng)
3. [docker-compose.yml giải thích từng dòng](#3-docker-composeyml-giải-thích-từng-dòng)
4. [Các lệnh Docker cơ bản](#4-các-lệnh-docker-cơ-bản)
5. [Quy trình chạy đầy đủ](#5-quy-trình-chạy-đầy-đủ)
6. [Xem log và debug](#6-xem-log-và-debug)
7. [Lỗi thường gặp và cách fix](#7-lỗi-thường-gặp-và-cách-fix)
8. [Kiến thức Docker nền tảng](#8-kiến-thức-docker-nền-tảng)

---

## 1. Tổng quan project

Project này là **Spring Boot 3.3.6 + Java 17** backend với các thành phần:

```
┌─────────────────────────────────────────┐
│            Docker Compose               │
│                                         │
│  ┌──────────┐  ┌───────┐  ┌─────────┐  │
│  │   App    │  │ MySQL │  │  Redis  │  │
│  │  :2807   │  │ :3306 │  │  :6379  │  │
│  └──────────┘  └───────┘  └─────────┘  │
│        │            │           │       │
│        └────────────┴───────────┘       │
│              beawsplatform_default      │
│              (Docker Network)           │
└─────────────────────────────────────────┘
```

**Tại sao cần Docker?**
- Đóng gói app + môi trường vào 1 "hộp" (container)
- Chạy được trên mọi máy, không cần cài Java, Maven thủ công
- Dễ dàng deploy lên AWS ECS, EC2, hoặc bất kỳ server nào

---

## 2. Dockerfile giải thích từng dòng

```
project/
├── Dockerfile          ← file này
├── docker-compose.yml
├── .dockerignore
└── src/
```

### Toàn bộ Dockerfile

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /build/target/demo-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p logs && chown -R spring:spring /app

USER spring

EXPOSE 2807

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-cp", "app.jar", \
  "com.ldang03.core.MainApplication"]
```

### Giải thích chi tiết Stage 1 (Build)

```dockerfile
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
```
- `FROM` = chọn image gốc để bắt đầu
- `maven:3.9-eclipse-temurin-17-alpine` = image có sẵn Maven 3.9 + Java 17 + Alpine Linux (nhẹ ~200MB)
- `AS builder` = đặt tên cho stage này là "builder" để stage sau tham chiếu

```dockerfile
WORKDIR /build
```
- Tạo thư mục `/build` trong container và cd vào đó
- Mọi lệnh sau đều chạy trong thư mục này

```dockerfile
COPY pom.xml ./
RUN mvn dependency:go-offline -B
```
- Copy **chỉ** `pom.xml` vào trước (không copy source code)
- Download tất cả dependencies
- **Tại sao tách riêng?** Docker cache — nếu `pom.xml` không đổi, bước này được cache lại,
  build lần sau sẽ bỏ qua bước download (tiết kiệm vài phút)
- `-B` = batch mode (không hỏi input)

```dockerfile
COPY src ./src
RUN mvn package -DskipTests -B
```
- Copy source code vào
- Build JAR file: `mvn package`
- `-DskipTests` = bỏ qua test để build nhanh hơn
- Output: `/build/target/demo-0.0.1-SNAPSHOT.jar`

### Giải thích chi tiết Stage 2 (Runtime)

```dockerfile
FROM eclipse-temurin:17-jre-alpine
```
- Image thứ 2, chỉ cần JRE (Java Runtime) không cần JDK (Java Development Kit)
- JRE nhẹ hơn JDK ~100MB
- Stage này **không có Maven**, chỉ để chạy app

```dockerfile
WORKDIR /app
```
- Thư mục làm việc trong container runtime

```dockerfile
RUN addgroup -S spring && adduser -S spring -G spring
```
- Tạo group `spring` và user `spring`
- `-S` = system user (không có home directory, không login được)
- **Tại sao?** Bảo mật: không chạy app bằng root

```dockerfile
COPY --from=builder /build/target/demo-0.0.1-SNAPSHOT.jar app.jar
```
- Copy JAR từ stage `builder` sang stage này
- **Multi-stage build**: stage 1 build xong, stage 2 chỉ lấy JAR
- Kết quả: image cuối không có Maven, source code — chỉ có JRE + JAR (~300MB thay vì ~700MB)

```dockerfile
RUN mkdir -p logs && chown -R spring:spring /app
```
- Tạo thư mục logs
- `chown` = đổi chủ sở hữu thư mục sang user `spring`

```dockerfile
USER spring
```
- Từ đây, mọi lệnh chạy bằng user `spring` (không phải root)

```dockerfile
EXPOSE 2807
```
- Khai báo container lắng nghe port 2807
- Đây chỉ là **metadata** (thông tin), không tự động mở port ra host

```dockerfile
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-cp", "app.jar", \
  "com.ldang03.core.MainApplication"]
```
- Lệnh chạy khi container start
- `-Djava.security.egd=file:/dev/./urandom` = tăng tốc random number generation trong container
- `-cp app.jar` = classpath (dùng thay vì `-jar` vì `spring-boot-maven-plugin` bị skip trong dự án này)
- `com.ldang03.core.MainApplication` = tên class có method `main()`

> **Tại sao dùng `-cp` thay vì `-jar`?**
> `pom.xml` có `spring-boot-maven-plugin` với `<skip>true</skip>` — plugin này bị tắt vì project
> dùng Lambda. Maven chỉ tạo shaded JAR (flat JAR). Để chạy flat JAR cần chỉ định class
> main bằng `-cp` thay vì `-jar`.

---

## 3. docker-compose.yml giải thích từng dòng

docker-compose dùng để chạy **nhiều container cùng lúc** và chúng có thể giao tiếp với nhau.

```yaml
version: '3.8'        # Phiên bản cú pháp docker-compose (cũ, hiện không cần)

services:             # Danh sách các container sẽ chạy
```

### Service: app (Spring Boot)

```yaml
  app:
    build: .          # Build image từ Dockerfile trong thư mục hiện tại
    container_name: be-aws-platform   # Tên container (thay vì tên random)
    ports:
      - "2807:2807"   # host_port:container_port — truy cập localhost:2807
```

```yaml
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/aws_exam
```
- Biến môi trường ghi đè `application.properties`
- `db` = tên service MySQL bên dưới (Docker DNS tự resolve)
- Spring Boot tự map: `SPRING_DATASOURCE_URL` → `spring.datasource.url`

```yaml
      SPRING_DATA_REDIS_HOST: redis
```
- `redis` = tên service Redis bên dưới
- Ghi đè `spring.data.redis.host=localhost` trong application.properties

```yaml
    depends_on:
      db:
        condition: service_healthy   # Chờ MySQL healthy rồi mới start app
      redis:
        condition: service_started   # Chờ Redis start (không cần health check)
    restart: unless-stopped          # Tự restart nếu crash, trừ khi dừng thủ công
```

### Service: db (MySQL)

```yaml
  db:
    image: mysql:8.0            # Dùng image có sẵn từ Docker Hub, không cần build
    container_name: mysql-exam
    environment:
      MYSQL_ROOT_PASSWORD: <your-root-password>     # Password cho user root
      MYSQL_DATABASE: aws_exam                      # Tự tạo database này khi khởi động
      MYSQL_USER: admin                             # Tạo thêm user này
      MYSQL_PASSWORD: <your-admin-password>         # Password cho user admin
    ports:
      - "3306:3306"             # Expose để dùng MySQL Workbench từ host
    volumes:
      - mysql-data:/var/lib/mysql       # Lưu data vào volume, không mất khi container xóa
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", ...]  # Kiểm tra MySQL sẵn sàng chưa
      interval: 10s             # Kiểm tra mỗi 10s
      timeout: 5s               # Timeout mỗi lần check
      retries: 5                # Thử lại 5 lần rồi mới báo unhealthy
```

### Service: redis

```yaml
  redis:
    image: redis:7-alpine
    container_name: redis-exam
    # Không expose port ra host vì:
    # 1. Chỉ app cần kết nối Redis, không cần từ bên ngoài
    # 2. Tránh xung đột nếu máy đã có Redis chạy trên port 6379
    restart: unless-stopped
```

### Volumes

```yaml
volumes:
  mysql-data:    # Docker tạo và quản lý volume này
                 # Data MySQL được lưu ở đây, tồn tại kể cả khi container bị xóa
```

---

## 4. Các lệnh Docker cơ bản

### Build image

```bash
# Build image từ Dockerfile trong thư mục hiện tại
docker build -t be-aws-platform:latest .

# Giải thích:
# docker build        = lệnh build
# -t be-aws-platform  = đặt tên (tag) cho image
# :latest             = version tag (mặc định nếu không ghi)
# .                   = Dockerfile nằm ở thư mục hiện tại
```

```bash
# Build với tag phiên bản cụ thể
docker build -t be-aws-platform:1.0.0 .

# Build không dùng cache (build lại từ đầu)
docker build --no-cache -t be-aws-platform:latest .
```

### Quản lý image

```bash
# Xem danh sách image
docker images

# Xóa image
docker rmi be-aws-platform:latest

# Xóa tất cả image không dùng
docker image prune
```

### Chạy container đơn lẻ (không dùng compose)

```bash
# Chạy container
docker run -d \
  --name be-aws-platform \
  -p 2807:2807 \
  -e SPRING_DATA_REDIS_HOST="your-redis-host" \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://your-db:3306/aws_exam" \
  be-aws-platform:latest

# Giải thích:
# docker run          = tạo và chạy container
# -d                  = detached mode (chạy ngầm)
# --name              = đặt tên container
# -p 2807:2807        = map port host:container
# -e KEY=VALUE        = truyền biến môi trường
```

```bash
# Dừng container
docker stop be-aws-platform

# Start lại container đã dừng
docker start be-aws-platform

# Xóa container (phải stop trước)
docker rm be-aws-platform

# Dừng và xóa luôn
docker rm -f be-aws-platform
```

### Quản lý container

```bash
# Xem container đang chạy
docker ps

# Xem tất cả container (kể cả đã stop)
docker ps -a

# Xem thông tin chi tiết container
docker inspect be-aws-platform
```

---

## 5. Quy trình chạy đầy đủ

### Lần đầu tiên (từ đầu)

```bash
# Bước 1: Vào thư mục project
cd C:\study\AWSdemo\BEAWSPlatForm

# Bước 2: Build image và start tất cả service
docker-compose up --build

# --build = luôn build lại image trước khi chạy
# Lần đầu sẽ mất 5-10 phút (download dependencies)
```

### Chạy ngầm (background)

```bash
docker-compose up -d --build

# -d = detached mode, chạy ngầm, trả lại terminal
```

### Sau khi đã build rồi (không có code thay đổi)

```bash
docker-compose up -d
# Không cần --build, dùng image đã build sẵn
```

### Khi có thay đổi code

```bash
# Build lại image rồi restart app
docker-compose up -d --build app

# Chỉ rebuild service "app", không rebuild mysql/redis
```

### Dừng tất cả

```bash
# Dừng nhưng giữ container và data
docker-compose stop

# Dừng và xóa container (data MySQL vẫn còn trong volume)
docker-compose down

# Dừng, xóa container VÀ xóa volume (mất hết data MySQL!)
docker-compose down -v
```

---

## 6. Xem log và debug

### Xem log

```bash
# Xem log của tất cả service
docker-compose logs

# Xem log realtime (follow)
docker-compose logs -f

# Xem log chỉ service app
docker-compose logs -f app

# Xem 50 dòng cuối
docker-compose logs --tail=50 app

# Hoặc dùng docker logs trực tiếp
docker logs be-aws-platform
docker logs be-aws-platform -f          # follow
docker logs be-aws-platform --tail 100  # 100 dòng cuối
```

### Vào trong container

```bash
# Mở terminal trong container app đang chạy
docker exec -it be-aws-platform sh

# Giải thích:
# exec    = chạy lệnh trong container đang running
# -it     = interactive + tty (mở terminal tương tác)
# sh      = shell (Alpine dùng sh, Ubuntu dùng bash)

# Ví dụ: kiểm tra kết nối Redis từ trong container
docker exec -it be-aws-platform sh -c "wget -q -O- redis:6379"

# Thoát terminal container
exit
```

### Kiểm tra network

```bash
# Xem danh sách network
docker network ls

# Xem container nào trong network
docker network inspect beawsplatform_default

# Kiểm tra IP của container
docker inspect be-aws-platform --format "{{.NetworkSettings.Networks}}"
```

### Kiểm tra app đang chạy

```bash
# Test HTTP endpoint (cần có token nếu có auth)
curl http://localhost:2807/api/topics

# Kiểm tra port có mở không
curl -s -o /dev/null -w "%{http_code}" http://localhost:2807/api/topics
# 401 = endpoint hoạt động, cần token
# 200 = OK
# 000 = app chưa chạy
```

---

## 7. Lỗi thường gặp và cách fix

### Lỗi 1: Connection refused (Redis/MySQL)

```
Caused by: ClosingException: Failed to create client - Connection refused
```

**Nguyên nhân:** App chạy đơn bằng `docker run` không có Redis/MySQL.

**Fix:** Dùng `docker-compose up` thay vì `docker run`.

---

### Lỗi 2: Port already allocated

```
Bind for 0.0.0.0:6379 failed: port is already allocated
```

**Nguyên nhân:** Port đó đã bị dùng bởi container hoặc service khác.

**Fix:**
```bash
# Xem container nào đang chiếm port 6379
docker ps --filter "publish=6379"

# Hoặc đổi port trong docker-compose.yml
ports:
  - "6380:6379"   # host:container — dùng 6380 trên host thay vì 6379
```

---

### Lỗi 3: Container không join Docker network

**Triệu chứng:** App nói `bad address 'redis'` hoặc `redis timed out`.

**Kiểm tra:**
```bash
docker network inspect beawsplatform_default
# Xem có đủ 3 container không: app, db, redis
```

**Fix:**
```bash
# Kết nối thủ công container vào network
docker network connect beawsplatform_default redis-exam --alias redis

# Hoặc restart toàn bộ stack
docker-compose down && docker-compose up -d
```

---

### Lỗi 4: MySQL chưa ready khi app start

```
Communications link failure
```

**Nguyên nhân:** App start trước khi MySQL khởi tạo xong.

**Fix:** Đã được xử lý trong `docker-compose.yml` bằng:
```yaml
depends_on:
  db:
    condition: service_healthy
```
MySQL phải pass health check trước khi app được phép start.

---

### Lỗi 5: Image cũ, code mới không có hiệu lực

**Fix:**
```bash
# Build lại image và restart
docker-compose up -d --build app

# Hoặc nếu muốn build không cache
docker-compose build --no-cache app
docker-compose up -d
```

---

### Lỗi 6: Out of disk space

```bash
# Xem dung lượng Docker đang dùng
docker system df

# Dọn dẹp: xóa image/container/cache không dùng
docker system prune

# Dọn dẹp triệt để (xóa cả volume!)
docker system prune -a --volumes
```

---

## 8. Kiến thức Docker nền tảng

### Image vs Container

```
Image  = bản thiết kế (blueprint) — như file .exe
         Không chạy, chỉ lưu trữ

Container = instance đang chạy — như process
            Tạo từ Image, có thể có nhiều container từ 1 image
```

### Layer và Cache

```
Dockerfile mỗi lệnh RUN/COPY/ADD = 1 layer
Docker cache lại từng layer
Nếu layer không đổi → bỏ qua, build nhanh hơn

Vì vậy:
  COPY pom.xml ./              ← layer này ít đổi → cache lâu
  RUN mvn dependency:go-offline ← được cache khi pom.xml không đổi
  COPY src ./src               ← layer này hay đổi
  RUN mvn package              ← chạy lại khi src thay đổi
```

### Multi-stage Build

```
Ý tưởng: dùng nhiều FROM trong 1 Dockerfile
Stage 1 (builder): có Maven, JDK, source code → build JAR
Stage 2 (runtime): chỉ có JRE + JAR → image nhỏ, an toàn

Kết quả:
  Nếu không multi-stage: ~700MB (có Maven, JDK, source code)
  Nếu multi-stage:       ~300MB (chỉ JRE + JAR)
```

### Docker Network

```
Khi dùng docker-compose, tự động tạo 1 network riêng
Các container trong cùng network:
  - Giao tiếp với nhau qua tên service (DNS nội bộ)
  - Ví dụ: app kết nối mysql bằng hostname "db", không cần IP

Từ host machine → container: dùng localhost:PORT
Từ container → container:    dùng service-name:PORT
```

### Volume

```
Container bị xóa → data bên trong mất hết
Volume = nơi lưu data bên ngoài container

Trong docker-compose:
  volumes:
    - mysql-data:/var/lib/mysql   ← volume_name:đường_dẫn_trong_container

docker volume ls            → xem danh sách volume
docker volume rm mysql-data → xóa volume (mất hết data!)
```

### Biến môi trường (Environment Variables)

```
Spring Boot tự động map env var sang property:
  SPRING_DATASOURCE_URL  →  spring.datasource.url
  SPRING_DATA_REDIS_HOST →  spring.data.redis.host

Quy tắc:
  - Chữ hoa
  - Dấu . → _
  - Dấu - → _

Ưu tiên: env var > application.properties
```

---

## Tham khảo nhanh (cheat sheet)

```bash
# Build và chạy lần đầu
docker-compose up -d --build

# Rebuild sau khi đổi code
docker-compose up -d --build app

# Xem log realtime
docker-compose logs -f app

# Dừng tất cả
docker-compose down

# Xóa sạch (kể cả data)
docker-compose down -v

# Vào trong container debug
docker exec -it be-aws-platform sh

# Kiểm tra app chạy chưa
curl -s -o /dev/null -w "%{http_code}" http://localhost:2807/api/topics
```
