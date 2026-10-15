"""Legacy read/modify/save progress, with no retry, atomic merge or new business validation."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.ports.progress import ProgressHeartbeat, ProgressRecord, ProgressStore


@dataclass(frozen=True)
class ProgressUpdates:
    store: ProgressStore
    clock: Callable[[], datetime]

    def update(self, heartbeat: ProgressHeartbeat) -> None:
        if heartbeat.player_status != "playing":
            return
        record = self.store.find(heartbeat.video_id, heartbeat.viewer_id, heartbeat.client_id)
        if record is None:
            record = ProgressRecord(None, heartbeat.video_id, heartbeat.viewer_id, heartbeat.client_id,
                                    None, None, self.clock(), self.clock())
        else:
            record = replace(record, updated_at=self.clock())
        self.store.save(replace(record, session_id=heartbeat.session_id, milliseconds=heartbeat.player_time))
