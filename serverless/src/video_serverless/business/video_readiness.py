"""Legacy all-lane status calculation, retaining READY and missing-reference behavior."""

from dataclasses import dataclass

from video_serverless.ports.video_readiness import LaneState, ReadinessStore

_FINISHED = frozenset({"LOCAL_TRANSCODE", "ALIYUN_CLOUD_FUNCTION_TRANSCODE",
                       "ALIYUN_CLOUD_FUNCTION_TRANSCODE_CPU", "ALIYUN_CLOUD_FUNCTION_TRANSCODE_GPU"})


def lane_succeeded(lane: LaneState) -> bool:
    provider = lane.provider if lane.current_provider is None else lane.current_provider
    if provider == "ALIYUN_MPS_TRANSCODE":
        return lane.status == "TranscodeSuccess"
    return provider in _FINISHED and lane.status == "FINISHED"


@dataclass(frozen=True)
class VideoReadiness:
    store: ReadinessStore

    def advance(self, video_id: str, transcode_ids: tuple[str, ...], current_status: str | None) -> str | None:
        lanes = self.store.lanes(transcode_ids)
        if not lanes:
            return current_status
        successes = sum(lane_succeeded(lane) for lane in lanes)
        failures = sum(lane.status == "FAILED" for lane in lanes)
        if successes == len(lanes):
            status = "READY"
        elif failures == len(lanes):
            status = "TRANSCODE_FAILED"
        elif successes:
            status = "TRANSCODING_PARTLY_COMPLETE"
        else:
            status = "TRANSCODING"
        if current_status == "READY" or current_status == status:
            return current_status
        self.store.update_status(video_id, status)
        return status
