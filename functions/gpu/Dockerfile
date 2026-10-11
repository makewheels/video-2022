# GPU 函数镜像：基于官方 NVIDIA ffmpeg 构建（含 NVENC 编码器）。
# FC 平台注入 NVIDIA 驱动；镜像只带 CUDA 用户态运行时与 ffmpeg。
# P3 云端首验时用 `ffmpeg -encoders | grep nvenc` 确认 hevc_nvenc/h264_nvenc 实际可用。
FROM registry.cn-shanghai.aliyuncs.com/serverless_devs/nvidia-ffmpeg:latest

# 该镜像基于 debian/ubuntu + nvidia ffmpeg；补 python 运行时
RUN apt-get update --fix-missing && apt-get install -y --no-install-recommends \
    python3 python3-pip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip3 install --no-cache-dir -r requirements.txt

COPY common/ ./common/
COPY gpu/ ./gpu/

EXPOSE 9000
ENTRYPOINT ["python3", "-u", "/usr/src/app/gpu/main.py"]
