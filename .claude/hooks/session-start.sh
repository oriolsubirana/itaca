#!/bin/bash
# SessionStart hook for Claude Code on the web: prepares the Ítaca dev environment.
# Idempotent; only runs in remote (web) sessions.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# 1. JDK 25 (backend toolchain). The apt index in the container can be stale.
if [ ! -d /usr/lib/jvm/java-25-openjdk-amd64 ]; then
  sudo apt-get update -qq || true
  sudo apt-get install -y -qq openjdk-25-jdk-headless
fi

# 2. Docker daemon (needed by Testcontainers integration tests)
if ! docker ps > /dev/null 2>&1; then
  sudo dockerd > /tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 30); do
    docker ps > /dev/null 2>&1 && break
    sleep 1
  done
fi

# 3. Docker Hub mirror for Testcontainers (Docker Hub rate-limits unauthenticated pulls)
if [ ! -f "$HOME/.testcontainers.properties" ]; then
  echo "hub.image.name.prefix=mirror.gcr.io/" > "$HOME/.testcontainers.properties"
fi

# 4. Pre-pull the Postgres image used by the integration tests (best effort)
docker image inspect mirror.gcr.io/library/postgres:17-alpine > /dev/null 2>&1 \
  || docker pull -q mirror.gcr.io/library/postgres:17-alpine || true

# 5. Backend: download dependencies and compile main + test classes (warms Gradle cache)
(cd backend && ./gradlew --no-daemon -q classes testClasses)

# 6. Frontend: install dependencies
(cd frontend && npm install --no-audit --no-fund)

echo "Ítaca session ready: JDK 25, dockerd, Testcontainers mirror, Gradle and npm caches warm"
