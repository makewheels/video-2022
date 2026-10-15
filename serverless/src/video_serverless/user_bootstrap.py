"""Explicit isolated composition, with real randomness; no automatic route registration."""

import secrets
import traceback
from datetime import UTC, datetime
from time import time_ns

from video_serverless.adapters.mongo_avatar_files import MongoAvatarFiles
from video_serverless.adapters.mongo_exception_journal import MongoExceptionJournal
from video_serverless.adapters.mongo_identifiers import MongoIdSequences
from video_serverless.adapters.mongo_login import MongoLoginStore
from video_serverless.adapters.mongo_profile_updates import MongoProfileWrites
from video_serverless.adapters.mongo_request_journal import MongoRequestJournal
from video_serverless.adapters.mongo_users import MongoUserReads
from video_serverless.adapters.mongo_verification_codes import MongoVerificationCodes
from video_serverless.adapters.mongo_watching_ids import MongoWatchingIds
from video_serverless.business.identifiers import Identifiers
from video_serverless.business.login import Login
from video_serverless.business.profile_updates import ProfileUpdates
from video_serverless.business.profiles import Profiles
from video_serverless.business.request_identity import IdentityResolver
from video_serverless.business.users import Users
from video_serverless.business.verification_codes import CodeRequests
from video_serverless.business.watching_ids import WatchingIds
from video_serverless.http import Response
from video_serverless.ports.exception_journal import ExceptionEntry
from video_serverless.request_cycle import RequestCycle, unknown_error_response
from video_serverless.user_application import UserApplication, UserRequestError


def build_user_application(database_provider, avatar_urls, *, profile: str, local_clock,
                           worker: int, datacenter: int) -> UserApplication:
    # No dev default: the caller must supply the legacy profile and timezone explicitly.
    utc_clock = lambda: datetime.now(UTC)  # noqa: E731
    clock_ms = lambda: time_ns() // 1_000_000  # noqa: E731
    sequences = MongoIdSequences(database_provider, worker, datacenter)
    identifiers = Identifiers(sequences, local_clock, clock_ms,
                              lambda: f"{secrets.randbelow(1000):03d}", worker, datacenter)
    reads = MongoUserReads(database_provider)
    errors = MongoExceptionJournal(database_provider)

    def error_response(error):
        domain = isinstance(error, UserRequestError)
        errors.save(ExceptionEntry(utc_clock(), None if domain else str(error),
                                   "".join(traceback.format_exception(error)),
                                   error.code if domain else None, error.message if domain else None))
        if domain:
            return Response(200, Response.json({"code": error.code, "message": error.message, "data": None}).body,
                            {"Content-Type": "application/json"})
        return unknown_error_response(error)

    return UserApplication(
        CodeRequests(MongoVerificationCodes(database_provider), lambda: f"{secrets.randbelow(10000):04d}", utc_clock),
        Login(MongoLoginStore(database_provider), identifiers.user_id, identifiers.token, utc_clock, profile == "prod"),
        Users(reads), Profiles(reads, MongoAvatarFiles(database_provider), avatar_urls),
        ProfileUpdates(MongoProfileWrites(database_provider), utc_clock),
        WatchingIds(MongoWatchingIds(database_provider), utc_clock),
        RequestCycle(IdentityResolver(reads), MongoRequestJournal(database_provider), clock_ms, error_response),
    )
