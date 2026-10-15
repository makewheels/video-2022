"""Legacy watch counter/event ordering, including both IP lookups and partial failures."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.ports.watch_log import IpLocations, WatchEvent, WatchLogStore, WatchVisit


@dataclass(frozen=True)
class WatchLogs:
    store: WatchLogStore
    locations: IpLocations
    clock: Callable[[], datetime]

    def add(self, visit: WatchVisit, requested_status: str) -> int:
        # Includes the controller's existence check before the service's dedup query.
        if not self.store.video_exists(visit.video_id):
            return 22
        if self.store.duplicate(visit.video_id, visit.session_id, requested_status):
            return 0
        video = self.store.video(visit.video_id)
        if video is None:
            return 22
        if requested_status == "READY":
            self.store.increment(visit.video_id)
        now = self.clock()
        info = self.locations.lookup(visit.ip)
        event = WatchEvent(replace(visit, video_id=video.id), video.status, now, info)
        self.store.save(event)
        # The legacy second lookup is for log output but its failure still affects the response.
        logged_info = self.locations.lookup(visit.ip)
        if logged_info is None:
            raise ValueError("Legacy watch log IP output cannot dereference null")
        return 0
