# ==========================================
# Video-2022 Docker 多阶段构建
# Stage 1: 构建前端 (web/)
# Stage 2: 构建后端 (server/)
# Stage 3: 运行时 (JRE 21)
# ==========================================

# --- Stage 1: 构建前端 ---
FROM node:20-alpine AS frontend-builder
WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --prefer-offline
COPY web/ ./
# 输出到 server 的 static 目录 (与 vite.config.ts outDir 一致)
RUN npm run build -- --outDir /build/static --emptyOutDir

# --- Stage 2: 构建后端 ---
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /build

# 先复制 pom.xml 以利用 Docker 缓存
COPY server/pom.xml ./server/pom.xml
COPY server/video/pom.xml ./server/video/pom.xml
COPY server/youtube/pom.xml ./server/youtube/pom.xml

# 下载依赖 (可缓存)
RUN cd server && mvn dependency:go-offline -pl video -am -Pspringboot --batch-mode --no-transfer-progress 2>/dev/null || true

# 复制源码
COPY server/ ./server/

# 复制前端构建产物到 static 资源目录
COPY --from=frontend-builder /build/static/ ./server/video/src/main/resources/static/

# 构建 JAR
RUN cd server && mvn package -DskipTests -pl video -am -Pspringboot --batch-mode --no-transfer-progress

# --- Stage 3: 运行时 ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

COPY --from=backend-builder /build/server/video/target/video-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 5022

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:5022/healthCheck || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
