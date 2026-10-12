FROM python:3.11-slim-bookworm

# ffmpeg 走 Debian 官方源（remux/probe/cover 均为软解，无需 NVENC）
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:0.9.26 /uv /uvx /bin/
WORKDIR /usr/src/app

COPY cpu/requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/uv uv pip install --system -r requirements.txt

COPY common/ ./common/
COPY cpu/ ./cpu/

EXPOSE 9000
ENTRYPOINT ["python3", "-u", "/usr/src/app/cpu/main.py"]
