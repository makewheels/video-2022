"""Legacy watch-id lookup and viewer-first progress identity; no watch/heartbeat writes."""

from bson import ObjectId

from video_serverless.adapters.mongo_users import _long, _text
from video_serverless.ports.watch_info import SavedProgress, WatchVideo


class MongoWatchReads:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def exists(self, watch_id):
        return self._database_provider().get_collection("video").find_one(
            {"watch.watchId": watch_id}, {"_id": 1}
        ) is not None

    def video(self, watch_id):
        document = self._database_provider().get_collection("video").find_one({"watch.watchId": watch_id})
        if document is None:
            return None
        return WatchVideo(_text(document["_id"]), _text(document.get("uploaderId")),
                          _text(document.get("visibility")), _text(document.get("status", "CREATED")),
                          _text(document.get("coverId")))

    def cover_key(self, cover_id):
        if cover_id is None:
            return None
        key = ObjectId(cover_id) if ObjectId.is_valid(cover_id) else cover_id
        cover = self._database_provider().get_collection("cover").find_one({"_id": key}, {"key": 1})
        return None if cover is None else _text(cover.get("key"))

    def progress(self, video_id, viewer_id, client_id):
        if viewer_id is None and client_id is None:
            raise ValueError("viewerId and clientId cannot both be absent")
        query = {"videoId": video_id}
        query["viewerId" if viewer_id is not None else "clientId"] = viewer_id if viewer_id is not None else client_id
        record = self._database_provider().get_collection("progress").find_one(query)
        return None if record is None else SavedProgress(_long(record.get("progressInMillis")))
