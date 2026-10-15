"""Explicit watch-info assembly, sharing the caller's request lifecycle and Mongo pool."""

import secrets
from time import time_ns

from video_serverless.adapters.hmac_access_signatures import HmacAccessSignatures
from video_serverless.adapters.mongo_watch_info import MongoWatchReads
from video_serverless.business.playback_authorization import PlaybackAuthorization
from video_serverless.business.watch_info import WatchInfos
from video_serverless.watch_application import WatchApplication


def build_watch_application(database_provider, urls, cycle, *, signature_secret, internal_base_url,
                            clock_ms=None, nano_id=None):
    if clock_ms is None:
        def clock_ms():
            return time_ns() // 1_000_000
    if nano_id is None:
        def nano_id():
            alphabet = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return "".join(secrets.choice(alphabet) for _ in range(21))
    authorization = PlaybackAuthorization(HmacAccessSignatures(signature_secret, clock_ms), clock_ms, nano_id)
    return WatchApplication(WatchInfos(MongoWatchReads(database_provider), urls, authorization,
                                       internal_base_url, nano_id), cycle)
