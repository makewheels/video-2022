FROM nvidia/cuda:12.4.1-devel-ubuntu22.04 AS ffmpeg-build
ARG FFMPEG_VERSION=n7.1.1
ARG NV_CODEC_VERSION=n12.1.14.0
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates git build-essential pkg-config nasm libx264-dev \
    && rm -rf /var/lib/apt/lists/*
RUN set -eu; temp_root="$(realpath "${TMPDIR:-/tmp}")"; \
    task_dir="$(mktemp -d "$temp_root/video-gpu-build.XXXXXX")"; \
    trap 'case "$task_dir" in "$temp_root"/video-gpu-build.*) rm -rf "$task_dir";; *) exit 1;; esac' EXIT; \
    git clone --depth 1 --branch "$NV_CODEC_VERSION" https://github.com/FFmpeg/nv-codec-headers.git "$task_dir/headers"; \
    make -C "$task_dir/headers" install; \
    git clone --depth 1 --branch "$FFMPEG_VERSION" https://github.com/FFmpeg/FFmpeg.git "$task_dir/ffmpeg"; \
    cd "$task_dir/ffmpeg"; \
    ./configure --prefix=/opt/ffmpeg --enable-gpl --enable-libx264 --enable-nvenc \
        --disable-debug --disable-doc; \
    make -j2; make install

FROM ghcr.io/astral-sh/uv:0.12.2 AS uv
FROM nvidia/cuda:12.4.1-runtime-ubuntu22.04
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates libx264-163 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=uv /uv /usr/local/bin/uv
COPY --from=ffmpeg-build /opt/ffmpeg /opt/ffmpeg
ENV PATH="/opt/worker/.venv/bin:/opt/ffmpeg/bin:${PATH}" \
    UV_PYTHON_INSTALL_DIR=/opt/python \
    PYTHONDONTWRITEBYTECODE=1 \
    NVIDIA_DRIVER_CAPABILITIES=compute,utility,video
WORKDIR /opt/worker
COPY pyproject.toml uv.lock ./
COPY video_transcode_worker ./video_transcode_worker
RUN set -eu; temp_root="$(realpath "${TMPDIR:-/tmp}")"; \
    task_dir="$(mktemp -d "$temp_root/video-gpu-deps.XXXXXX")"; \
    trap 'case "$task_dir" in "$temp_root"/video-gpu-deps.*) rm -rf "$task_dir";; *) exit 1;; esac' EXIT; \
    UV_CACHE_DIR="$task_dir/cache" uv sync --locked --no-dev --python 3.12
EXPOSE 9000
CMD ["video-transcode-worker", "serve"]
