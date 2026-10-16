"""Existing transcode $in and video status-only update, without schema changes or retries."""

from video_serverless.adapters.mongo_local_covers import _id
from video_serverless.adapters.mongo_users import _text
from video_serverless.ports.video_readiness import LaneState


class MongoVideoReadiness:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def lanes(self, transcode_ids):
        if transcode_ids is None:
            raise ValueError("Legacy transcode IDs must be supplied")
        query = {"_id": {"$in": [_id(value) for value in transcode_ids]}}
        documents = self._database_provider().transcode.find(
            query, {"provider": 1, "currentProvider": 1, "status": 1})
        return tuple(LaneState(_text(row.get("provider")), _text(row.get("currentProvider")),
                               _text(row.get("status", "CREATED"))) for row in documents)

    def update_status(self, video_id, status):
        self._database_provider().video.update_one({"_id": _id(video_id)}, {"$set": {"status": status}})
