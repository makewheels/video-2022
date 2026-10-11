"""Task validation, OSS publication, and completion notification."""

import re
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from threading import Lock

from .media import RESOLUTIONS, segments, transcode


@dataclass(frozen=True)
class Task:
    job_id: str
    transcode_id: str
    input_key: str
    output_dir: str
    resolution: str

    @classmethod
    def parse(cls, body: dict):
        job_id, transcode_id = body["jobId"], body["transcodeId"]
        if not all(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,128}", value)
                   for value in (job_id, transcode_id)):
            raise ValueError("Invalid task ID")
        input_key, output_dir = body["inputKey"], body["outputDir"]
        for key in (input_key, output_dir):
            if not isinstance(key, str) or not key.startswith("videos/") or any(
                part in {"", ".", ".."} for part in key.split("/")
            ) or "\\" in key or any(ord(char) < 32 for char in key):
                raise ValueError("Invalid OSS key")
        # Match current OssPathUtil's isolated directory for each transcode.
        source_prefix = input_key.split("/raw/", 1)
        if len(source_prefix) != 2 or output_dir != source_prefix[0] + "/transcode/" + transcode_id:
            raise ValueError("Output directory does not belong to source video")
        resolution = body["resolution"]
        if resolution not in RESOLUTIONS:
            raise ValueError("Unsupported resolution")
        return cls(job_id, transcode_id, input_key, output_dir, resolution)

    @property
    def playlist_key(self):
        return f"{self.output_dir}/{self.transcode_id}.m3u8"


class Worker:
    def __init__(self, store, notify, encoder="h264_nvenc", timeout=3600):
        self.store = store
        self.notify = notify
        self.encoder = encoder
        self.timeout = timeout
        self.lock = Lock()

    def run(self, task: Task):
        # Prototype has a single instance. Cross-instance deduplication is a deployment gate.
        if not self.lock.acquire(blocking=False):
            raise RuntimeError("Worker is busy")
        try:
            self._publish(task)
            self.notify(task.job_id)
        finally:
            self.lock.release()

    def _publish(self, task):
        if self.store.object_exists(task.playlist_key):
            content = self.store.get_object(task.playlist_key).read().decode("utf-8")
            names = segments(content, task.transcode_id)
            if any(self.store.head_object(f"{task.output_dir}/{name}").content_length <= 0 for name in names):
                raise ValueError("Published playlist references an empty segment")
            return
        with tempfile.TemporaryDirectory(prefix="video-gpu-task-") as directory:
            root = Path(directory)
            source = root / ("input" + PurePosixPath(task.input_key).suffix)
            self.store.get_object_to_file(task.input_key, str(source))
            files = transcode(source, root / "output", task.transcode_id, task.resolution,
                              self.encoder, self.timeout)
            for path in files:  # media.transcode puts the playlist last.
                self.store.put_object_from_file(f"{task.output_dir}/{path.name}", str(path))
