"""Save heartbeat before progress, retaining partial writes and request-local identity."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.business.progress import ProgressUpdates
from video_serverless.ports.heartbeat import Heartbeat, HeartbeatStore
from video_serverless.ports.progress import ProgressHeartbeat
from video_serverless.ports.users import UserSnapshot


@dataclass(frozen=True)
class Heartbeats:
    store: HeartbeatStore
    progress: ProgressUpdates
    clock: Callable[[], datetime]

    def add(self, heartbeat: Heartbeat, user: UserSnapshot | None = None) -> None:
        saved = replace(heartbeat, viewer_id=heartbeat.viewer_id if user is None else user.id, created_at=self.clock())
        self.store.save(saved)
        self.progress.update(ProgressHeartbeat(saved.video_id, saved.viewer_id, saved.client_id,
                                              saved.session_id, saved.player_time, saved.player_status))
