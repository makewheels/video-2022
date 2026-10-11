#!/usr/bin/env bash
# FC 3.0 函数创建/更新（幂等）。
# 用法: fc_upsert.sh <functionName(service$fn)> <image:tag> <gpu|empty> <target>
# 环境变量（Infisical 注入）:
#   FC_DEPLOY_AK_ID/FC_DEPLOY_AK_SECRET  已由 aliyun configure set 配置
#   ALIYUN_OSS_VIDEO_ACCESS_KEY_ID / ALIYUN_OSS_VIDEO_SECRET_KEY  函数运行 OSS 凭证
#   CALLBACK_SECRET  回调与调用共用密钥（INVOKE_SECRET 未配置时复用）
#   ALIYUN_CF_TRANSCODE_INVOKE_SECRET  可选，覆盖 INVOKE_SECRET
# 说明: 异步重试固定 0（应用层统一重试）；HTTP 触发器 anonymous + 应用层 X-Invoke-Secret 校验。
set -euo pipefail

FN_NAME=$1
IMAGE=$2
GPU_MODE=$3
TARGET=$4

INVOKE_SECRET_VALUE="${ALIYUN_CF_TRANSCODE_INVOKE_SECRET:-${CALLBACK_SECRET:?CALLBACK_SECRET 未配置}}"

GPU_BODY='"gpuConfig": {"gpuMemorySize": 6144},"cpu": 3,"memorySize": 12288,'
if [ "$GPU_MODE" != "gpu" ]; then
  GPU_BODY='"cpu": 2,"memorySize": 4096,'
fi

# python 构造 payload，避免 shell 拼接转义问题
PAYLOAD=$(INVOKE_SECRET_VALUE="$INVOKE_SECRET_VALUE" OSS_AK_ID="${ALIYUN_OSS_VIDEO_ACCESS_KEY_ID:?}" \
OSS_SK="${ALIYUN_OSS_VIDEO_SECRET_KEY:?}" CB_SECRET="${CALLBACK_SECRET:?}" \
GPU_BODY="$GPU_BODY" IMAGE="$IMAGE" FN_NAME="$FN_NAME" LOG_PROJECT="video-2022-logs" LOG_STORE="transcode-fn${TARGET:+-$TARGET}" python3 - <<'PYEOF'
import json, os
payload = {
    "functionName": os.environ["FN_NAME"],
    "runtime": "custom-container",
    "handler": "index.handler",
    "timeout": 1800,
    "diskSize": 10240,
    "instanceConcurrency": 1,
    "customContainerConfig": {"image": os.environ["IMAGE"], "command": [], "args": [], "webServer": True},
    "cpu": None, "memorySize": None, "gpuConfig": None,
    "environmentVariables": {
        "TZ": "Asia/Shanghai",
        "OSS_ACCESS_KEY_ID": os.environ["OSS_AK_ID"],
        "OSS_SECRET_KEY": os.environ["OSS_SK"],
        "CALLBACK_SECRET": os.environ["CB_SECRET"],
        "INVOKE_SECRET": os.environ["INVOKE_SECRET_VALUE"],
    },
    "asyncInvokeConfig": {"maxAsyncRetryAttempts": 0},
    "logConfig": {
        "project": os.environ["LOG_PROJECT"], "logstore": "transcode-fn",
        "enableRequestMetrics": True, "enableInstanceMetrics": True,
    },
    "internetAccess": True,
}
# GPU 规格（GPU_BODY 形如 '"gpuConfig": {...},"cpu": 3,"memorySize": 12288,'）
import re
body = os.environ["GPU_BODY"]
for key, val in re.findall(r'"(\w+)":\s*({[^}]*}|\d+)', body):
    payload[key] = json.loads(val) if val.startswith('{') else int(val)
payload = {k: v for k, v in payload.items() if v is not None}
print(json.dumps(payload, ensure_ascii=False))
PYEOF
)

urlenc() { python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

# aliyun CLI 路径不支持 $ 字符，GetFunction 用 API 名形式探测
if aliyun fc GetFunction --functionName "$FN_NAME" >/dev/null 2>&1; then
  echo "函数已存在，更新: $FN_NAME"
  aliyun fc UpdateFunction --functionName "$FN_NAME" --body "$PAYLOAD" >/dev/null && echo "updated"
else
  echo "创建新函数: $FN_NAME"
  aliyun fc CreateFunction --body "$PAYLOAD" >/dev/null && echo "created"
  aliyun fc CreateTrigger --functionName "$FN_NAME" --body '{
    "triggerName": "defaultTrigger",
    "triggerType": "http",
    "qualifier": "LATEST",
    "triggerConfig": {"authType": "anonymous", "methods": ["POST", "GET"]}
  }' >/dev/null && echo "trigger created"
fi

URL=$(aliyun fc ListTriggers --functionName "$FN_NAME" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    for t in d.get('triggers', []):
        ht=t.get('httpTrigger') or {}
        if ht.get('urlInternet'): print(ht['urlInternet'])
except Exception:
    pass
" | head -1 || true)
echo "部署完成: $FN_NAME"
echo "触发器 URL: ${URL:-<查询失败，用 FC API 复查>}"
