# Hướng dẫn Deploy Spring Boot lên AWS EC2 với Docker

## Mục lục
1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Dockerfile](#2-dockerfile)
3. [Docker Compose](#3-docker-compose)
4. [AWS Setup](#4-aws-setup)
5. [deploy.sh](#5-deploysh)
6. [GitHub Actions CI/CD](#6-github-actions-cicd)
7. [Các lỗi đã gặp và cách fix](#7-các-lỗi-đã-gặp-và-cách-fix)
8. [Lệnh thường dùng](#8-lệnh-thường-dùng)

---

## 1. Tổng quan kiến trúc

```
GitHub Push
    │
    ▼
GitHub Actions
    │  (SSH vào EC2)
    ▼
EC2 Ubuntu (ubuntu@ip-172-31-41-12)
    │
    ├── deploy.sh
    │     ├── git pull code mới
    │     ├── lấy credentials từ AWS Secrets Manager
    │     ├── tạo file .env
    │     └── docker compose up
    │
    ├── Docker Containers
    │     ├── be-aws-platform (Spring Boot :2807)
    │     └── redis-exam      (Redis :6379)
    │
    └── Nginx (reverse proxy → :2807)
          │
          ▼
    examplatform.cloud
```

**External Services:**
- **AWS RDS MySQL** — database chính (endpoint lấy từ RDS console)
- **AWS Secrets Manager** — lưu username/password của RDS (tự động rotate)
- **AWS S3** — lưu file
- **AWS Cognito** — JWT authentication

---

## 2. Dockerfile

```dockerfile
# Stage 1: Build JAR bằng Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn dependency:go-offline -B       # cache dependency trước
COPY src ./src
RUN mvn package -DskipTests -B         # build JAR

# Stage 2: Chạy app với JRE nhẹ
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/target/demo-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p logs && chown -R spring:spring /app
USER spring
EXPOSE 2807
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-cp", "app.jar", "com.ldang03.core.MainApplication"]
```

**Tại sao dùng `-cp` thay vì `-jar`?**

Project dùng `maven-shade-plugin` với `<skip>true</skip>` (tắt repackage của spring-boot-maven-plugin). JAR tạo ra không phải là executable fat JAR kiểu Spring Boot thông thường. Phải dùng `-cp app.jar com.ldang03.core.MainApplication` để chỉ định class main trực tiếp.

**Build và chạy thủ công:**
```bash
# Build image
docker build -t be-aws-platform .

# Chạy container (không dùng cách này trong production — dùng docker compose)
docker run -d \
  --name be-aws-platform \
  -p 2807:2807 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host:3306/db" \
  be-aws-platform
```

---

## 3. Docker Compose

File `docker-compose.yml`:

```yaml
services:
  app:
    build: .
    container_name: be-aws-platform
    ports:
      - "2807:2807"
    env_file:
      - .env          # đọc SPRING_DATASOURCE_* từ file này
    environment:
      SPRING_DATA_REDIS_HOST: redis   # tên service = hostname trong Docker network
      SPRING_DATA_REDIS_PORT: 6379
      AWS_REGION: ap-southeast-1
      AWS_S3_BUCKET: examdocuments
      LOGGING_LEVEL_ROOT: INFO
      LOGGING_LEVEL_COM_LDANG03: DEBUG
    depends_on:
      redis:
        condition: service_started
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: redis-exam
    restart: unless-stopped
    # Không expose port ra host — chỉ dùng trong Docker network nội bộ
```

**File `.env` (gitignored, tạo bởi deploy.sh):**
```
SPRING_DATASOURCE_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/<DB_NAME>
SPRING_DATASOURCE_USERNAME=<DB_USER>
SPRING_DATASOURCE_PASSWORD=<PASSWORD_WITH_$$_ESCAPED>
```
> Ví dụ nếu password có `$`: `P@$$w0rd` → viết trong .env là `P@$$$$w0rd` (mỗi `$` thành `$$`)

> **Quan trọng:** Password có ký tự `$`. Docker Compose dùng `$$` để escape thành `$` literal.
> Trong `.env` phải viết `$$` thì container mới nhận được `$` đúng.

**Lệnh thường dùng:**
```bash
docker compose up -d            # start tất cả services (detached)
docker compose up -d --build    # rebuild image rồi start
docker compose down             # stop và remove containers
docker compose ps               # xem trạng thái
docker compose logs app -f      # xem log app realtime
```

---

## 4. AWS Setup

### 4.1 RDS MySQL

- **Endpoint:** `<tên-db>.xxxx.ap-southeast-1.rds.amazonaws.com` (lấy từ RDS Console)
- **Port:** 3306
- **Database:** `<tên-database>`
- **Master user:** `admin`
- Password được quản lý bởi **Secrets Manager** (tự rotate)

**Security Group của RDS phải allow inbound port 3306 từ EC2 Security Group.**

### 4.2 AWS Secrets Manager

Secret ARN: `arn:aws:secretsmanager:<REGION>:<ACCOUNT_ID>:secret:rds!<SECRET_ID>`

Format của secret (chỉ có username và password, không có host/port):
```json
{
  "username": "admin",
  "password": "<your-rds-password>"
}
```

**Lấy secret bằng CLI:**
```bash
aws secretsmanager get-secret-value \
  --region ap-southeast-1 \
  --secret-id 'arn:aws:secretsmanager:...' \
  --query SecretString \
  --output text
```

**EC2 cần IAM Role** với quyền `secretsmanager:GetSecretValue` để chạy lệnh trên.

### 4.3 EC2 Setup

- **OS:** Ubuntu 22.04
- **User:** ubuntu
- **Project dir:** `~/app/BEExamPlatform`
- **Deploy script link:** `~/deploy/deploy.sh → ~/app/BEExamPlatform/deploy.sh`

Cài đặt cần thiết trên EC2:
```bash
# Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu

# AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install

# Clone project
mkdir -p ~/app
cd ~/app
git clone git@github.com:dangthanhloc0/BEExamPlatform.git

# Tạo symlink cho deploy script
mkdir -p ~/deploy
ln -s ~/app/BEExamPlatform/deploy.sh ~/deploy/deploy.sh
```

---

## 5. deploy.sh

File `deploy.sh` làm 5 việc:

```bash
#!/bin/bash
set -e   # exit ngay nếu lệnh nào lỗi

main() {
  # 1. Pull code mới từ GitHub
  cd "$PROJECT_DIR"
  git fetch origin main
  git reset --hard origin/main   # dùng reset thay vì pull để tránh conflict

  # 2. Lấy credentials từ AWS Secrets Manager
  SECRET=$(aws secretsmanager get-secret-value ...)

  # 3. Tạo file .env (dùng heredoc để tránh bash expand $)
  cat > /tmp/write_env.py << 'PYEOF'
import json, sys
s = json.loads(sys.stdin.read())
with open(sys.argv[3], 'w') as f:
    f.write('SPRING_DATASOURCE_URL=...\n')
    f.write('SPRING_DATASOURCE_USERNAME=' + s['username'] + '\n')
    f.write('SPRING_DATASOURCE_PASSWORD=' + s['password'].replace('$', '$$') + '\n')
PYEOF
  echo "$SECRET" | python3 /tmp/write_env.py "$DB_HOST" "$DB_NAME" "$PROJECT_DIR/.env"

  # 4. Stop container cũ
  docker rm -f be-aws-platform 2>/dev/null || true

  # 5. Build và start
  docker compose up -d --build
}

main "$@"
```

**Tại sao dùng `main()` function?**

Khi bash chạy script, nó đọc file từng dòng một. Nếu `git reset --hard` thay đổi nội dung script đang chạy, bash sẽ đọc lẫn code cũ và code mới → lỗi. Bọc trong `main()` buộc bash phải parse toàn bộ function vào memory trước khi chạy.

**Tại sao dùng heredoc `<< 'PYEOF'` (single-quoted)?**

Trong bash, `$$` bên trong `"..."` (double-quoted string) bị expand thành PID của process. Nếu viết Python code trực tiếp trong `python3 -c "..."`, thì `$$` trong `replace('$', '$$')` sẽ bị bash thay bằng PID số. Dùng heredoc single-quoted `<< 'PYEOF'` thì bash không expand bất kỳ thứ gì bên trong.

**Tại sao phải escape `$` thành `$$`?**

Docker Compose (kể cả khi dùng `env_file:`) vẫn thực hiện variable interpolation trên giá trị. Nó thấy `$VARNAME` → coi là biến → expand thành empty string → password bị cắt → "Access denied".

Viết `$$VARNAME` thì Docker Compose hiểu `$$` là escape của `$` → container nhận được `$VARNAME` đúng.

---

## 6. GitHub Actions CI/CD

File `.github/workflows/deploy.yml`:

```yaml
name: Deploy Spring Boot
on:
  push:
    branches:
      - main

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: SSH Deploy
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            bash ~/deploy/deploy.sh
```

**GitHub Secrets cần cài:**

| Secret | Giá trị |
|--------|---------|
| `EC2_HOST` | IP public của EC2 (vd: `54.x.x.x`) |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Nội dung private key (ed25519) |

**Cách tạo SSH key cho deploy:**
```bash
# Trên EC2
ssh-keygen -t ed25519 -f ~/.ssh/deploy_key -N ""

# Thêm public key vào authorized_keys
cat ~/.ssh/deploy_key.pub >> ~/.ssh/authorized_keys

# Copy private key để paste vào GitHub Secret EC2_SSH_KEY
cat ~/.ssh/deploy_key
```

**Flow khi push code:**
```
git push origin main
    → GitHub Actions trigger
    → SSH vào EC2
    → chạy bash ~/deploy/deploy.sh
    → git pull + tạo .env + docker compose up --build
```

---

## 7. Các lỗi đã gặp và cách fix

### Lỗi 1: Redis Connection refused

```
org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis
```

**Nguyên nhân:** App chạy standalone bằng `docker run`, không cùng network với Redis container.

**Fix:** Dùng `docker compose up` thay vì `docker run`. Các service trong cùng docker-compose tự động join chung network, liên lạc với nhau qua tên service (`redis`).

---

### Lỗi 2: Port already allocated

```
Error: Bind for 0.0.0.0:6379 failed: port is already allocated
```

**Nguyên nhân:** Redis container cũ vẫn đang chiếm port 6379 trên host.

**Fix:** Xóa `ports:` trong redis service. Redis không cần expose ra host, chỉ cần accessible trong Docker network nội bộ.

---

### Lỗi 3: docker-compose KeyError ContainerConfig

```
KeyError: 'ContainerConfig'
```

**Nguyên nhân:** `docker-compose` (version 1 — Python) có bug với Docker Engine mới.

**Fix:** Dùng `docker compose` (version 2 — Go, tích hợp trong Docker CLI) thay vì `docker-compose`.

---

### Lỗi 4: SSH no key found

```
ssh: no key found
```

**Nguyên nhân:** Private key trong GitHub Secret bị format sai (thiếu newline, hoặc copy không đủ).

**Fix:** Tạo key mới bằng `ed25519`, copy đúng toàn bộ nội dung từ `-----BEGIN` đến `-----END`.

---

### Lỗi 5: aws command not found

```
aws: command not found
```

**Nguyên nhân:** Khi SSH non-interactive, PATH không load đầy đủ. AWS CLI cài ở `/usr/local/bin` không nằm trong PATH mặc định.

**Fix:** Thêm vào đầu deploy.sh:
```bash
export PATH=$PATH:/usr/local/bin:/usr/bin:/snap/bin
```

---

### Lỗi 6: git pull cần credential (private repo)

**Nguyên nhân:** Repository private, HTTPS yêu cầu username/password.

**Fix:** Đổi remote sang SSH:
```bash
git remote set-url origin git@github.com:dangthanhloc0/BEExamPlatform.git
```
Thêm EC2's public key vào GitHub Deploy Keys (Settings → Deploy Keys).

---

### Lỗi 7: Script tự sửa chính mình (self-modifying script)

```
KeyError: 'host'   # hoặc các lỗi kỳ lạ khác sau git reset --hard
```

**Nguyên nhân:** Bash đọc script từng dòng một. Khi `git reset --hard` thay đổi file script đang chạy, bash đọc tiếp nhưng là code của version mới trong khi biến/state là của version cũ.

**Fix:** Bọc toàn bộ logic vào `main()` function:
```bash
main() {
  # ... tất cả code vào đây
}
main "$@"
```
Bash sẽ parse toàn bộ function vào memory trước khi execute dòng đầu tiên.

---

### Lỗi 8: Secret KeyError 'host' — format sai

**Nguyên nhân:** Giả định secret có field `host`, nhưng AWS RDS-managed secret chỉ có:
```json
{"username": "admin", "password": "..."}
```
Không có host, port, hay dbname.

**Fix:** Hardcode DB_HOST và DB_NAME trong script, chỉ lấy username/password từ secret.

---

### Lỗi 9: Password bị cắt — $VAR expansion

```
Access denied for user 'admin'@'172.31.41.12' (using password: YES)
```
Container nhận password bị cắt, thiếu phần có ký tự `$`.

**Nguyên nhân chuỗi:**

1. Password có ký tự `$` bên trong (ví dụ: `P@$sword`)
2. Docker Compose expand `$sword` → coi là biến → empty string → password thành `P@`
3. Password bị cắt → sai → "Access denied"

**Fix:** Escape `$` thành `$$` trong `.env` file khi viết bằng Python:
```python
s['password'].replace('$', '$$')
```

**Trap thêm:** Nếu Python code nằm trong `python3 -c "..."` (bash double-quoted string), thì `'$$'` bên trong sẽ bị bash expand thành PID. Phải dùng heredoc single-quoted:

```bash
cat > /tmp/script.py << 'PYEOF'
# Python code ở đây — bash không expand gì cả
s['password'].replace('$', '$$')  # '$$' là literal Python string
PYEOF
python3 /tmp/script.py
```

---

### Lỗi 10: 502 Bad Gateway (Nginx)

**Nguyên nhân:** App container chưa start hoặc đang crash, Nginx không forward được đến `:2807`.

**Fix:** Kiểm tra app có đang chạy không rồi mới check Nginx:
```bash
docker compose ps            # app phải ở trạng thái "Up"
curl http://localhost:2807/api/topics   # test trực tiếp bỏ qua Nginx
```

---

## 8. Lệnh thường dùng

### Kiểm tra status

```bash
docker compose ps                          # xem containers
docker logs be-aws-platform --tail 50      # xem log gần nhất
docker logs be-aws-platform -f             # follow log realtime
docker inspect be-aws-platform --format '{{json .Config.Env}}' | python3 -m json.tool  # xem env vars
```

### Kiểm tra password trong container

```bash
docker inspect be-aws-platform --format '{{json .Config.Env}}' | python3 -c "
import json, sys
envs = json.load(sys.stdin)
for e in envs:
    if 'PASSWORD' in e:
        key, val = e.split('=', 1)
        print('Length:', len(val))
"
```

### Tạo lại .env thủ công

```bash
cat > /tmp/write_env.py << 'PYEOF'
import json, sys
s = json.loads(sys.stdin.read())
with open(sys.argv[1], 'w') as f:
    f.write('SPRING_DATASOURCE_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/<DB_NAME>\n')
    f.write('SPRING_DATASOURCE_USERNAME=' + s['username'] + '\n')
    f.write('SPRING_DATASOURCE_PASSWORD=' + s['password'].replace('$', '$$') + '\n')
PYEOF

aws secretsmanager get-secret-value \
  --region ap-southeast-1 \
  --secret-id '<YOUR_SECRET_ARN>' \
  --query SecretString \
  --output text | python3 /tmp/write_env.py ~/app/BEExamPlatform/.env

cat ~/app/BEExamPlatform/.env  # kiểm tra — password phải có $$
```

### Đưa application-local.properties lên server (làm 1 lần)

`application-local.properties` chứa SMTP credentials, CloudFront domain, Cognito config... không có trong `.env` hay `docker-compose.yml`. Cần SCP lên EC2 một lần — file này là **untracked** nên `git reset --hard` không xóa nó.

```bash
# Từ máy local
scp -i <key.pem> src/main/resources/application-local.properties \
  ubuntu@<EC2_IP>:~/app/BEExamPlatform/application-local.properties
```

`docker-compose.yml` đã mount file này vào container:
```yaml
volumes:
  - ./application-local.properties:/app/application-local.properties:ro
```

Spring Boot tự load vì WORKDIR của container là `/app` và file nằm đúng đó.

**Thứ tự ưu tiên config (cao → thấp):**
1. `environment:` trong docker-compose → override Redis host = "redis"
2. `env_file: .env` → override DB credentials (từ Secrets Manager)
3. `application-local.properties` (mount) → SMTP, CloudFront, Cognito, S3 key

---

### Restart app thủ công

```bash
cd ~/app/BEExamPlatform
docker rm -f be-aws-platform
docker compose up -d
```

### Deploy thủ công (không cần GitHub Actions)

```bash
bash ~/deploy/deploy.sh
```

### Test API (bypass Nginx)

```bash
# Trực tiếp qua port 2807
curl http://localhost:2807/api/topics

# Qua Nginx (domain)
curl https://examplatform.cloud/api/topics
```

---

## File quan trọng

| File | Mục đích | Git? |
|------|---------|------|
| `Dockerfile` | Build image | ✅ commit |
| `docker-compose.yml` | Orchestrate containers | ✅ commit |
| `deploy.sh` | Script deploy tự động | ✅ commit |
| `.github/workflows/deploy.yml` | GitHub Actions CI/CD | ✅ commit |
| `application.properties` | Config chung (không có secret) | ✅ commit |
| `application-local.properties` | Config local (có secret) | ❌ gitignore |
| `.env` | DB credentials cho Docker | ❌ gitignore |
| `.env.example` | Template của .env | ✅ commit |
