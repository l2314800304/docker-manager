# ===== Build Frontend =====
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build-only

# ===== Build Backend (with frontend bundled) =====
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src/ ./src/

# Copy frontend build output into Spring Boot static resources
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/

RUN mvn package -DskipTests -B

# ===== Runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install docker CLI for compose operations
RUN apk add --no-cache docker-cli docker-cli-compose

COPY --from=backend-build /app/target/*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
