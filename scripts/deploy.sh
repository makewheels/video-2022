#!/bin/bash
set -e

# ==========================================
# Video-2022 服务端部署脚本
# 自动检测并安装环境依赖，构建并部署应用
# ==========================================

REPO_DIR="/opt/video-2022/repo"
APP_DIR="/opt/video-2022/backend"
CURRENT_DIR="$APP_DIR/current"
RELEASE_DIR="$APP_DIR/releases/$(date +%Y%m%d%H%M%S)"
PORTAL_DIR="/opt/video-2022/developer-portal"
ENV_FILE="$APP_DIR/.env"
SERVICE_NAME="video-2022"

JAVA_VERSION="21"
NODE_VERSION="20"

# ==========================================
# 环境检测与自动安装
# ==========================================

ensure_dirs() {
    echo "=== 检查目录结构 ==="
    mkdir -p "$APP_DIR" "$CURRENT_DIR" "$PORTAL_DIR" "$REPO_DIR"
}

install_java() {
    if java -version 2>&1 | grep -q "version \"$JAVA_VERSION"; then
        echo "✅ Java $JAVA_VERSION 已安装"
        return
    fi
    echo "📦 安装 Java $JAVA_VERSION ..."
    apt-get update -qq
    apt-get install -y -qq openjdk-${JAVA_VERSION}-jdk-headless > /dev/null
    echo "✅ Java $JAVA_VERSION 安装完成"
}

install_maven() {
    if command -v mvn &> /dev/null; then
        echo "✅ Maven 已安装"
        return
    fi
    echo "📦 安装 Maven ..."
    apt-get update -qq
    apt-get install -y -qq maven > /dev/null
    echo "✅ Maven 安装完成"
}

install_node() {
    if command -v node &> /dev/null && node -v | grep -q "v${NODE_VERSION}"; then
        echo "✅ Node.js v$NODE_VERSION 已安装"
        return
    fi
    echo "📦 安装 Node.js v$NODE_VERSION ..."
    if ! command -v curl &> /dev/null; then
        apt-get update -qq && apt-get install -y -qq curl > /dev/null
    fi
    curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - > /dev/null 2>&1
    apt-get install -y -qq nodejs > /dev/null
    echo "✅ Node.js $(node -v) 安装完成"
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

setup_systemd_service() {
    local service_file="/etc/systemd/system/${SERVICE_NAME}.service"
    if [ -f "$service_file" ]; then
        echo "✅ systemd 服务已配置"
        return
    fi
    echo "📦 配置 systemd 服务 ..."
    cat > "$service_file" << 'UNIT'
[Unit]
Description=Video-2022 Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/video-2022/backend/current
EnvironmentFile=/opt/video-2022/backend/.env
ExecStart=/usr/bin/java -jar app.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
    systemctl daemon-reload
    systemctl enable "$SERVICE_NAME"
    echo "✅ systemd 服务配置完成"
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

    location /developer-portal/ {
        alias /opt/video-2022/developer-portal/;
        try_files $uri $uri/ /developer-portal/index.html;
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
    cat > "$ENV_FILE" << 'ENV'
# Video-2022 环境变量 — 请填写实际值
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5022

# MongoDB
SPRING_MONGODB_HOST=10.0.20.14
SPRING_MONGODB_PORT=27017
SPRING_MONGODB_DATABASE=video-2022
SPRING_MONGODB_USERNAME=video-2022
SPRING_MONGODB_PASSWORD=CHANGE_ME

# 管理接口
ADMIN_API_KEY=CHANGE_ME

# 开发者平台 JWT
DEVELOPER_JWT_SECRET=CHANGE_ME

# 阿里云（按需配置）
ALIYUN_OSS_VIDEO_ACCESS_KEY=
ALIYUN_OSS_VIDEO_SECRET_KEY=
ALIYUN_OSS_DATA_ACCESS_KEY=
ALIYUN_OSS_DATA_SECRET_KEY=
ALIYUN_MPS_ACCESS_KEY=
ALIYUN_MPS_SECRET_KEY=
ENV
    echo "⚠️  请编辑 $ENV_FILE 填写实际密钥"
}

# ==========================================
# 构建与部署
# ==========================================

echo "=========================================="
echo "  Video-2022 部署开始"
echo "=========================================="

ensure_dirs

echo ""
echo "=== 检测运行环境 ==="
install_java
install_maven
install_node
install_nginx
setup_systemd_service
setup_nginx
setup_env_file

echo ""
echo "=== 构建前端 ==="
cd "$REPO_DIR/frontend"
npm ci --prefer-offline 2>/dev/null || npm install
npm run build

echo ""
echo "=== 构建开发者门户 ==="
cd "$REPO_DIR/developer-portal"
npm ci --prefer-offline 2>/dev/null || npm install
npm run build

echo ""
echo "=== 部署开发者门户 ==="
rm -rf "$PORTAL_DIR"/*
cp -r "$REPO_DIR/developer-portal/dist"/* "$PORTAL_DIR/"

echo ""
echo "=== 构建后端 ==="
cd "$REPO_DIR/backend"
mvn package -DskipTests -pl video -am -Pspringboot --batch-mode --no-transfer-progress

echo ""
echo "=== 部署后端 ==="
mkdir -p "$RELEASE_DIR"
cp "$REPO_DIR/backend/video/target/video-0.0.1-SNAPSHOT.jar" "$RELEASE_DIR/app.jar"
cp "$RELEASE_DIR/app.jar" "$CURRENT_DIR/app.jar"

echo ""
echo "=== 重启服务 ==="
systemctl restart "$SERVICE_NAME"

echo ""
echo "=== 等待启动 ==="
for i in $(seq 1 30); do
    if curl -sf -o /dev/null http://localhost:5022/healthCheck 2>/dev/null; then
        echo "✅ 后端启动成功"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "❌ 后端启动超时"
        journalctl -u "$SERVICE_NAME" --no-pager -n 30
        exit 1
    fi
    sleep 2
done

echo ""
echo "=== 清理旧版本（保留最近5个） ==="
cd "$APP_DIR/releases"
ls -dt */ 2>/dev/null | tail -n +6 | xargs rm -rf 2>/dev/null || true

echo ""
echo "=========================================="
echo "  ✅ 部署成功！"
echo "=========================================="
