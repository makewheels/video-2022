#!/usr/bin/env bash
# FC 3.0 函数创建/更新（幂等）。用法：
#   fc_upsert.sh <functionName(service$fn)> <image with tag> <gpu|empty> <target>
# 依赖：aliyun CLI（fc 插件）、环境变量 FC_REQUEST_ID 前缀名。
# 说明：异步重试固定 0（应用层统一重试）；HTTP 触发器 anonymous + X-Invoke-Secret 应用层校验。
set -euo pipefail

FN_NAME=$1
IMAGE=$2
GPU_MODE=$3
TARGET=$4

GPU_BODY=""
if [ "$GPU_MODE" = "gpu" ]; then
  GPU_BODY='"gpuConfig": {"gpuMemorySize": 6144},"cpu": 3,"memorySize": 12288,'
fi

PAYLOAD=$(cat <<EOF
{
  "runtime": "custom-container",
  "handler": "index.handler",
  "timeout": 1800,
  "diskSize": 4096,
  "instanceConcurrency": 1,
  "customContainerConfig": {
    "image": "${IMAGE}",
    "command": [],
    "args": [],
    "webServer": true
  },
  ${GPU_BODY}
  "environmentVariables": {
    "TZ": "Asia/Shanghai",
    "OSS_ACCESS_KEY_ID": "\${OSS_ACCESS_KEY_ID}",
    "OSS_SECRET_KEY": "\${OSS_SECRET_KEY}",
    "CALLBACK_SECRET": "\${ALIYUN_CF_TRANSCODE_CALLBACK_SECRET}",
    "INVOKE_SECRET": "\${ALIYUN_CF_TRANSCODE_INVOKE_SECRET}"
  },
  "asyncInvokeConfig": {"maxAsyncRetryAttempts": 0},
  "logConfig": {
    "project": "video-2022-${TARGET}-logs",
    "logstore": "transcode-fn",
    "enableRequestMetrics": true,
    "enableInstanceMetrics": true
  },
  "internetAccess": true
}
EOF
)

# 变量替换：shell 里展开 ${OSS_*}（来自 Infisical 注入），保留镜像与函数名已展开的部分
PAYLOAD=$(eval echo \""$PAYLOAD"\" 2>/dev/null || printf '%s' "$PAYLOAD")

EXIST=$(aliyun fc GET "/2023-03-30/functions/$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$FN_NAME")" 2>/dev/null | head -c 20 || true)

if [ -n "$EXIST" ] && echo "$EXIST" | grep -q '{'; then
  echo "函数已存在，更新镜像与配置: $FN_NAME"
  ENC=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$FN_NAME")
  # aliyun CLI 的 PUT 路径不支持 $ 字符时改用 API 名形式
  aliyun fc UpdateFunction --functionName "$FN_NAME" --body "$PAYLOAD"
else
  echo "创建新函数: $FN_NAME"
  aliyun fc CreateFunction --body "$PAYLOAD"
  aliyun fc CreateTrigger --functionName "$FN_NAME" --body '{
    "triggerName": "defaultTrigger",
    "triggerType": "http",
    "qualifier": "LATEST",
    "triggerConfig": {"authType": "anonymous", "methods": ["POST", "GET"]}
  }'
fi

echo "部署完成: $FN_NAME ($IMAGE)"
