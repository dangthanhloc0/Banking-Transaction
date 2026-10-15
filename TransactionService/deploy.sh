#!/bin/bash
set -e

export PATH=$PATH:/usr/local/bin:/usr/bin:/snap/bin:/home/ubuntu/.local/bin

main() {
  PROJECT_DIR="$HOME/app/BEExamPlatform"
  SECRET_ARN='arn:aws:secretsmanager:ap-southeast-1:741011675298:secret:rds!db-2e36440e-4de2-477a-b2ff-85dc0ab5e0b9-7SHll7'
  DB_HOST="exam-cloud-platform.c96yiq6s4a6z.ap-southeast-1.rds.amazonaws.com"
  DB_NAME="aws_exam"

  echo "==> Move to project"
  cd "$PROJECT_DIR"

  echo "==> Pull latest source"
  git fetch origin main
  git reset --hard origin/main

  echo "==> Fetch credentials from AWS Secrets Manager"
  SECRET=$(aws secretsmanager get-secret-value \
    --region ap-southeast-1 \
    --secret-id "$SECRET_ARN" \
    --query SecretString \
    --output text)

  # Dùng heredoc single-quoted để Python code không bị bash expand $
  cat > /tmp/write_env.py << 'PYEOF'
import json, sys
s = json.loads(sys.stdin.read())
db_host = sys.argv[1]
db_name = sys.argv[2]
env_file = sys.argv[3]
with open(env_file, 'w') as f:
    f.write('SPRING_DATASOURCE_URL=jdbc:mysql://' + db_host + ':3306/' + db_name + '\n')
    f.write('SPRING_DATASOURCE_USERNAME=' + s['username'] + '\n')
    f.write('SPRING_DATASOURCE_PASSWORD=' + s['password'].replace('$', '$$') + '\n')
PYEOF

  echo "$SECRET" | python3 /tmp/write_env.py "$DB_HOST" "$DB_NAME" "$PROJECT_DIR/.env"

  echo "==> Stop old app container"
  docker rm -f be-aws-platform 2>/dev/null || true

  echo "==> Build image & start all containers"
  docker compose up -d --build

  echo "==> Status"
  docker compose ps

  echo "==> Deployment completed"
}

main "$@"
