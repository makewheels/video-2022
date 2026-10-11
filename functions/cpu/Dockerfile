FROM python:3.11-slim-bookworm

# ffmpeg 走 Debian 官方源（remux/probe/cover 均为软解，无需 NVENC）
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY common/ ./common/
COPY cpu/ ./cpu/

EXPOSE 9000
ENTRYPOINT ["python3", "-u", "/usr/src/app/cpu/main.py"]
