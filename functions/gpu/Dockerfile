# GPU 函数镜像：python slim + BtbN 静态 ffmpeg（GPL 构建，自带 h264_nvenc/hevc_nvenc）。
# FC 平台向 GPU 实例容器注入 NVIDIA 内核驱动；镜像只带 ffmpeg 与 python 运行时。
# P3 云端首验：`ffmpeg -encoders | grep nvenc` 确认 NVENC 实际可用。
FROM python:3.11-slim-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget ca-certificates xz-utils \
    && wget -q https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz \
    && tar xf ffmpeg-master-latest-linux64-gpl.tar.xz --strip-components=2 \
       -C /usr/local/bin --wildcards '*/bin/ffmpeg' '*/bin/ffprobe' \
    && rm -f ffmpeg-master-latest-linux64-gpl.tar.xz \
    && rm -rf /var/lib/apt/lists/* \
    && ffmpeg -version | head -1

WORKDIR /usr/src/app

COPY gpu/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY common/ ./common/
COPY gpu/ ./gpu/

EXPOSE 9000
ENTRYPOINT ["python3", "-u", "/usr/src/app/gpu/main.py"]
