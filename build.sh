#!/usr/bin/env bash
# Docker Manager 构建脚本 (DDD 多模块)
# Usage: ./build.sh [--skip-frontend] [--skip-tests]

set -e

SKIP_FRONTEND=false
SKIP_TESTS=false
MAVEN_ARGS=""

for arg in "$@"; do
  case $arg in
    --skip-frontend) SKIP_FRONTEND=true ;;
    --skip-tests) SKIP_TESTS=true; MAVEN_ARGS="$MAVEN_ARGS -DskipTests" ;;
  esac
done

echo "=== Docker Manager Build (DDD Multi-Module) ==="

# Step 1: Build frontend
if [ "$SKIP_FRONTEND" = false ]; then
  echo ""
  echo ">>> Building frontend..."
  cd frontend
  npm ci
  npm run build-only

  echo ">>> Copying frontend dist to docker-manager-starter/src/main/resources/static/..."
  rm -rf ../docker-manager-starter/src/main/resources/static
  cp -r dist/ ../docker-manager-starter/src/main/resources/static/
  cd ..
  echo ">>> Frontend build complete."
else
  echo ">>> Skipping frontend build."
fi

# Step 2: Build all Maven modules
echo ""
echo ">>> Building Maven modules..."
mvn clean package $MAVEN_ARGS -B

echo ""
echo "=== Build successful! ==="
echo "JAR file: docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar"
echo ""
echo "Run with: java -jar docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar"
