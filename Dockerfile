# ==========================================
# ScamLens Java Backend — Dockerfile for Render
# ==========================================

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy backend source code
COPY backend/ ./backend/

# Compile Java source code into bin/
RUN mkdir -p bin && javac -encoding UTF-8 -d bin backend/Server.java

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy compiled classes from build stage
COPY --from=builder /build/bin ./bin

# Render automatically injects the PORT environment variable; default to 8080 locally
ENV PORT=8080

EXPOSE 8080

# Run the lightweight Java HTTP Server
CMD ["java", "-cp", "bin", "Server"]
