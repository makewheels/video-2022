"""Legacy read/modify/save session lifecycle without ownership checks or application retries."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.ports.playback_sessions import (
    PlaybackSession,
    PlaybackSessionStore,
    PlaybackUpdate,
    StartPlayback,
)


@dataclass(frozen=True)
class PlaybackSessions:
    store: PlaybackSessionStore
    clock: Callable[[], datetime]

    def start(self, command: StartPlayback, user_id: str | None = None) -> str:
        now = self.clock()
        return self.store.save(PlaybackSession(
            watch_id=command.watch_id, video_id=command.video_id, client_id=command.client_id,
            session_id=command.session_id, user_id=user_id, started_at=now, created_at=now,
            updated_at=now, duration_ms=0, max_progress_ms=0, current_progress_ms=0,
            heartbeat_count=0, exit_type="PLAYING", resolution="auto"))

    def heartbeat(self, command: PlaybackUpdate) -> None:
        self._update(command, exiting=False)

    def exit(self, command: PlaybackUpdate) -> None:
        self._update(command, exiting=True)

    def _update(self, command: PlaybackUpdate, *, exiting: bool) -> None:
        session = self.store.find(command.id)
        if session is None:
            return
        maximum = session.max_progress_ms
        if command.current_time_ms is not None and maximum is not None and command.current_time_ms > maximum:
            maximum = command.current_time_ms
        fields = {"current_progress_ms": command.current_time_ms, "max_progress_ms": maximum}
        if command.duration_ms is not None:
            fields["duration_ms"] = command.duration_ms
        if command.resolution is not None:
            fields["resolution"] = command.resolution
        if exiting:
            fields["ended_at"] = self.clock()
            fields["exit_type"] = command.exit_type
        else:
            if session.heartbeat_count is None:
                raise ValueError("Legacy session heartbeat count is absent")
            fields["heartbeat_count"] = (session.heartbeat_count + 1 + 2**31) % 2**32 - 2**31
        fields["updated_at"] = self.clock()
        self.store.save(replace(session, **fields))
