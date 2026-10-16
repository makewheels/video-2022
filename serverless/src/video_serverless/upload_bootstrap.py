"""Opt-in ordinary creation assembly; no cloud providers, production routes or background workers."""

import secrets
from datetime import UTC, datetime
from time import time_ns

from video_serverless.adapters.mongo_file_uploads import MongoUploadFiles
from video_serverless.adapters.mongo_identifiers import MongoIdSequences
from video_serverless.adapters.mongo_video_creation import MongoCreationWrites
from video_serverless.adapters.mongo_video_status import MongoVideoStatus
from video_serverless.business.file_uploads import FileUploads
from video_serverless.business.identifiers import Identifiers
from video_serverless.business.video_creation import VideoCreation
from video_serverless.business.video_status import VideoStatuses
from video_serverless.file_upload_application import FileUploadApplication
from video_serverless.upload_application import UploadApplication
from video_serverless.video_status_application import VideoStatusApplication


def build_upload_application(database_provider, cycle, *, local_clock, local_timezone, worker, datacenter,
                             internal_base_url):
    if not internal_base_url:
        raise ValueError("Creation internal base URL must be explicit and nonempty")
    identifiers = Identifiers(MongoIdSequences(database_provider, worker, datacenter), local_clock,
                              lambda: time_ns() // 1_000_000, lambda: f"{secrets.randbelow(1000):03d}",
                              worker, datacenter)
    creation = VideoCreation(MongoCreationWrites(database_provider), identifiers,
                             lambda: datetime.now(UTC), local_timezone, internal_base_url)
    statuses = VideoStatusApplication(VideoStatuses(MongoVideoStatus(database_provider)), cycle)
    return UploadApplication(creation, cycle, statuses)


def build_file_upload_application(database_provider, cycle, objects):
    return FileUploadApplication(FileUploads(MongoUploadFiles(database_provider), objects), cycle)
