#!/bin/bash
set -e

# ==========================================
# Video-2022 Docker 部署脚本
# 从阿里云容器镜像服务拉取镜像并运行
# ==========================================

CONTAINER_NAME="video-2022"
APP_DIR="/opt/video-2022/server"
CONSOLE_DIR="/opt/video-2022/console"
ENV_FILE="$APP_DIR/.env"

# 从环境变量获取 Docker 配置 (由 CI 传入)
DOCKER_REGISTRY="${DOCKER_REGISTRY:-registry.cn-beijing.aliyuncs.com}"
DOCKER_IMAGE="${DOCKER_IMAGE:-registry.cn-beijing.aliyuncs.com/b4/docker-repo}"
IMAGE_TAG="${IMAGE_TAG:-video-2022-latest}"
DOCKER_USERNAME="${DOCKER_USERNAME:-}"
DOCKER_PASSWORD="${DOCKER_PASSWORD:-}"

# ==========================================
# 环境检测与安装
# ==========================================

install_docker() {
    if command -v docker &> /dev/null; then
        echo "✅ Docker 已安装: $(docker --version)"
        return
    fi
    echo "📦 安装 Docker ..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    echo "✅ Docker 安装完成: $(docker --version)"
}

install_nginx() {
    if command -v nginx &> /dev/null; then
        echo "✅ Nginx 已安装"
        return
    fi
    echo "📦 安装 Nginx ..."
    apt-get update -qq
    apt-get install -y -qq nginx > /dev/null
    echo "✅ Nginx 安装完成"
}

setup_nginx() {
    local conf_file="/etc/nginx/sites-available/video-2022"
    if [ -f "$conf_file" ]; then
        echo "✅ Nginx 配置已存在"
        return
    fi
    echo "📦 配置 Nginx 反向代理 ..."
    cat > "$conf_file" << 'NGINX'
server {
    listen 80;
    server_name _;

    location /console/ {
        alias /opt/video-2022/console/;
        try_files $uri $uri/ /console/index.html;
    }

    location / {
        proxy_pass http://127.0.0.1:5022;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX
    ln -sf "$conf_file" /etc/nginx/sites-enabled/video-2022
    rm -f /etc/nginx/sites-enabled/default
    nginx -t && systemctl reload nginx
    echo "✅ Nginx 配置完成"
}

setup_env_file() {
    if [ -f "$ENV_FILE" ]; then
        echo "✅ 环境变量文件已存在"
        return
    fi
    echo "⚠️  创建环境变量模板（请手动填写密钥）..."
    mkdir -p "$APP_DIR"
    cat > "$ENV_FILE" << 'ENV'
# Video-2022 Docker 容器环境变量 — 请填写实际值
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5022

# 禁用 SSL (由 Nginx 处理)
SERVER_SSL_ENABLED=false

# MongoDB
SPRING_DATA_MONGODB_HOST=10.0.20.14
SPRING_DATA_MONGODB_PORT=27017
SPRING_DATA_MONGODB_DATABASE=video-2022
SPRING_DATA_MONGODB_AUTHENTICATION_DATABASE=admin
SPRING_DATA_MONGODB_USERNAME=video-2022
SPRING_DATA_MONGODB_PASSWORD=CHANGE_ME

# 管理接口
ADMIN_API_KEY=CHANGE_ME

# 开发者平台 JWT
DEVELOPER_JWT_SECRET=CHANGE_ME

# 阿里云（按需配置）
ALIYUN_OSS_VIDEO_ACCESS_KEY_ID=
ALIYUN_OSS_VIDEO_SECRET_KEY=
ALIYUN_OSS_DATA_ACCESS_KEY_ID=
ALIYUN_OSS_DATA_SECRET_KEY=
ALIYUN_MPS_ACCESS_KEY_ID=
ALIYUN_MPS_SECRET_KEY=
ENV
    echo "⚠️  请编辑 $ENV_FILE 填写实际密钥"
}

# ==========================================
# Docker 部署
# ==========================================

echo "=========================================="
echo "  Video-2022 Docker 部署开始"
echo "=========================================="

# 确保目录
mkdir -p "$APP_DIR" "$CONSOLE_DIR"

echo ""
echo "=== 检测运行环境 ==="
install_docker
install_nginx
setup_nginx
setup_env_file

echo ""
echo "=== 登录镜像仓库 ==="
if [ -n "$DOCKER_PASSWORD" ] && [ -n "$DOCKER_USERNAME" ]; then
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin "$DOCKER_REGISTRY"
    echo "✅ 镜像仓库登录成功"
else
    echo "ℹ️  未提供凭据，跳过登录（使用已缓存的凭据）"
fi

echo ""
echo "=== 拉取镜像 ==="
FULL_IMAGE="${DOCKER_IMAGE}:${IMAGE_TAG}"
echo "拉取: $FULL_IMAGE"
docker pull "$FULL_IMAGE"
echo "✅ 镜像拉取完成"

echo ""
echo "=== 停止旧容器 ==="
if docker ps -q -f name="$CONTAINER_NAME" | grep -q .; then
    docker stop "$CONTAINER_NAME"
    docker rm "$CONTAINER_NAME"
    echo "✅ 旧容器已停止并移除"
else
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    echo "ℹ️  无运行中的旧容器"
fi

echo ""
echo "=== 启动新容器 ==="
docker run -d \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    --env-file "$ENV_FILE" \
    --network host \
    "$FULL_IMAGE"

echo "✅ 容器已启动"

echo ""
echo "=== 等待启动 ==="
for i in $(seq 1 30); do
    if curl -sf -o /dev/null http://localhost:5022/healthCheck 2>/dev/null; then
        echo "✅ 后端启动成功"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "❌ 后端启动超时"
        docker logs --tail 30 "$CONTAINER_NAME"
        exit 1
    fi
    sleep 2
done

echo ""
echo "=== 清理旧镜像 ==="
docker image prune -f --filter "until=168h" 2>/dev/null || true

echo ""
echo "=========================================="
echo "  ✅ Docker 部署成功！"
echo "  镜像: $FULL_IMAGE"
echo "  容器: $CONTAINER_NAME"
echo "=========================================="
