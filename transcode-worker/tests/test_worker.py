from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from video_transcode_worker import media
from video_transcode_worker.worker import Task, Worker

BODY = {"jobId": "job1", "transcodeId": "tc1", "inputKey": "videos/u/202610/v/raw/f/f.mp4",
        "outputDir": "videos/u/202610/v/transcode/tc1", "resolution": "720p"}
PLAYLIST = "#EXTM3U\n#EXTINF:5.0,\ntc1-00000.ts\n#EXT-X-ENDLIST\n"


@pytest.mark.parametrize("change", [
    {"jobId": "../job"}, {"transcodeId": "a;curl"}, {"inputKey": "videos/../secret"},
    {"outputDir": "videos/other/transcode/tc1"}, {"outputDir": BODY["outputDir"] + "/"},
    {"resolution": "480p"}, {"inputKey": "videos/u/202610/v/raw/../f.mp4"},
])
def test_rejects_unsafe_tasks(change):
    with pytest.raises(ValueError):
        Task.parse(BODY | change)


def test_command_uses_optional_audio_nvenc_and_argument_array():
    source = Path("source ; echo hacked.mp4")
    cmd = media.command(source, Path("out"), "tc1", "720p")
    assert cmd[cmd.index("-i") + 1] == str(source)
    assert "0:a:0?" in cmd
    assert "h264_nvenc" in cmd
    assert "min(1," in cmd[cmd.index("-vf") + 1]
    assert cmd[-1] == str(Path("out/tc1.m3u8"))


@pytest.mark.parametrize("content", [
    PLAYLIST.replace("#EXT-X-ENDLIST", ""), PLAYLIST.replace("tc1-00000.ts", "../other.ts"),
    PLAYLIST.replace("5.0", "0"), PLAYLIST.replace("5.0", "nan"),
    PLAYLIST.replace("#EXTINF:5.0,\n", ""),
])
def test_rejects_incomplete_or_unsafe_playlist(content):
    with pytest.raises(ValueError):
        media.segments(content, "tc1")


def test_publishes_segments_before_playlist_then_notifies(monkeypatch):
    events = []
    store, notify = Mock(), Mock(side_effect=lambda job: events.append("notify"))
    store.object_exists.return_value = False
    observed_dirs = []

    def encode(source, output, *args):
        observed_dirs.append(source.parent)
        output.mkdir()
        files = [output / "tc1-00000.ts", output / "tc1.m3u8"]
        for path in files:
            path.write_bytes(b"data")
        return files

    monkeypatch.setattr("video_transcode_worker.worker.transcode", encode)
    store.put_object_from_file.side_effect = lambda key, path: events.append(key)
    Worker(store, notify).run(Task.parse(BODY))
    assert events == [BODY["outputDir"] + "/tc1-00000.ts", BODY["outputDir"] + "/tc1.m3u8", "notify"]
    assert not observed_dirs[0].exists()


def test_encode_failure_never_notifies_and_cleans_temp(monkeypatch):
    store, notify = Mock(), Mock()
    store.object_exists.return_value = False
    observed_dirs = []

    def encode(source, *args):
        observed_dirs.append(source.parent)
        raise RuntimeError("encoder failed")

    monkeypatch.setattr("video_transcode_worker.worker.transcode", encode)
    worker = Worker(store, notify)
    with pytest.raises(RuntimeError):
        worker.run(Task.parse(BODY))
    notify.assert_not_called()
    store.put_object_from_file.assert_not_called()
    assert not observed_dirs[0].exists()
    assert not worker.lock.locked()


def test_failed_segment_upload_does_not_publish_playlist_or_notify(monkeypatch):
    store, notify = Mock(), Mock()
    store.object_exists.return_value = False
    observed_dirs = []

    def encode(source, output, *args):
        observed_dirs.append(source.parent)
        output.mkdir()
        files = [output / "tc1-00000.ts", output / "tc1.m3u8"]
        for path in files:
            path.write_bytes(b"data")
        return files

    monkeypatch.setattr("video_transcode_worker.worker.transcode", encode)
    store.put_object_from_file.side_effect = RuntimeError("OSS upload failed")
    with pytest.raises(RuntimeError):
        Worker(store, notify).run(Task.parse(BODY))
    notify.assert_not_called()
    assert store.put_object_from_file.call_count == 1
    assert store.put_object_from_file.call_args.args[0].endswith(".ts")
    assert not observed_dirs[0].exists()


def test_retry_reuses_published_output_after_callback_failure(monkeypatch):
    store, notify = Mock(), Mock(side_effect=[RuntimeError("callback unavailable"), None])
    store.object_exists.return_value = True
    store.get_object.return_value.read.return_value = PLAYLIST.encode()
    store.head_object.return_value = SimpleNamespace(content_length=100)
    encode = Mock()
    monkeypatch.setattr("video_transcode_worker.worker.transcode", encode)
    worker, task = Worker(store, notify), Task.parse(BODY)
    with pytest.raises(RuntimeError):
        worker.run(task)
    worker.run(task)
    encode.assert_not_called()
    store.put_object_from_file.assert_not_called()
    assert notify.call_count == 2


def test_retry_rejects_missing_or_empty_segment():
    store, notify = Mock(), Mock()
    store.object_exists.return_value = True
    store.get_object.return_value.read.return_value = PLAYLIST.encode()
    store.head_object.return_value = SimpleNamespace(content_length=0)
    with pytest.raises(ValueError):
        Worker(store, notify).run(Task.parse(BODY))
    notify.assert_not_called()


def test_busy_worker_does_not_start_duplicate_task():
    worker = Worker(Mock(), Mock())
    worker.lock.acquire()
    try:
        with pytest.raises(RuntimeError, match="busy"):
            worker.run(Task.parse(BODY))
        worker.store.object_exists.assert_not_called()
    finally:
        worker.lock.release()
