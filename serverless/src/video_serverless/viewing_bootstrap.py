"""Isolated composition; caller supplies database, signing and optional IP ports explicitly."""

from datetime import UTC, datetime

from video_serverless.adapters.mongo_heartbeat import MongoHeartbeatStore
from video_serverless.adapters.mongo_playback_sessions import MongoPlaybackSessionStore
from video_serverless.adapters.mongo_progress import MongoProgressStore
from video_serverless.adapters.mongo_watch_history import MongoHistoryCoverUrls, MongoHistoryStore
from video_serverless.adapters.mongo_watch_log import MongoWatchLogStore
from video_serverless.business.heartbeat import Heartbeats
from video_serverless.business.playback_sessions import PlaybackSessions
from video_serverless.business.progress import ProgressUpdates
from video_serverless.business.watch_history import WatchHistory
from video_serverless.business.watch_log import WatchLogs
from video_serverless.heartbeat_application import HeartbeatApplication
from video_serverless.history_progress_application import HistoryProgressApplication
from video_serverless.playback_application import PlaybackApplication
from video_serverless.viewing_application import ViewingApplication
from video_serverless.watch_log_application import WatchLogApplication


def build_viewing_application(database_provider, urls, cycle, *, ip_locations=None):
    clock = lambda: datetime.now(UTC)  # noqa: E731
    progress = MongoProgressStore(database_provider, clock=clock)
    history = WatchHistory(MongoHistoryStore(database_provider), MongoHistoryCoverUrls(database_provider, urls))
    heartbeats = Heartbeats(MongoHeartbeatStore(database_provider), ProgressUpdates(progress, clock), clock)
    playback = PlaybackSessions(MongoPlaybackSessionStore(database_provider), clock)
    # Never infer paid API credentials or call a supplier without an explicitly supplied capability.
    logs = None if ip_locations is None else WatchLogApplication(
        WatchLogs(MongoWatchLogStore(database_provider), ip_locations, clock), cycle)
    return ViewingApplication(HistoryProgressApplication(progress, history, cycle),
                              HeartbeatApplication(heartbeats, cycle), PlaybackApplication(playback, cycle), logs)
