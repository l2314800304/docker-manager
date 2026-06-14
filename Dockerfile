# ===== Build Frontend =====
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build-only

# ===== Build Backend (multi-module Maven project) =====
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-build
WORKDIR /app

# Copy parent POM and all module POMs first (for dependency caching)
COPY pom.xml ./
COPY docker-manager-domain/pom.xml ./docker-manager-domain/
COPY docker-manager-application/pom.xml ./docker-manager-application/
COPY docker-manager-infrastructure/pom.xml ./docker-manager-infrastructure/
COPY docker-manager-starter/pom.xml ./docker-manager-starter/
RUN mvn dependency:go-offline -B 2>/dev/null || true

# Copy all source code
COPY docker-manager-domain/src/ ./docker-manager-domain/src/
COPY docker-manager-application/src/ ./docker-manager-application/src/
COPY docker-manager-infrastructure/src/ ./docker-manager-infrastructure/src/
COPY docker-manager-starter/src/ ./docker-manager-starter/src/

# Copy frontend dist to starter module's static resources
COPY --from=frontend-build /app/frontend/dist/ ./docker-manager-starter/src/main/resources/static/

# Build all modules
RUN mvn package -DskipTests -B

# ===== Runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install docker CLI for compose operations
RUN apk add --no-cache docker-cli docker-cli-compose

# Copy the starter module's fat JAR
COPY --from=backend-build /app/docker-manager-starter/target/*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
