#!/usr/bin/env bash
# Build script for docker-manager
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

echo "=== Docker Manager Build ==="

# Step 1: Build frontend
if [ "$SKIP_FRONTEND" = false ]; then
  echo ""
  echo ">>> Building frontend..."
  cd frontend
  npm ci
  npm run build-only

  # Copy dist to Spring Boot static resources
  echo ">>> Copying frontend dist to src/main/resources/static/..."
  rm -rf ../src/main/resources/static
  cp -r dist/ ../src/main/resources/static/
  cd ..
  echo ">>> Frontend build complete."
else
  echo ">>> Skipping frontend build."
fi

# Step 2: Build backend with Maven
echo ""
echo ">>> Building backend with Maven..."
mvn clean package $MAVEN_ARGS -B

echo ""
echo "=== Build successful! ==="
echo "JAR file: target/docker-manager-1.0.0-SNAPSHOT.jar"
echo ""
echo "Run with: java -jar target/docker-manager-1.0.0-SNAPSHOT.jar"
