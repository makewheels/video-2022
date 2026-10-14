"""Uninstalled synchronous legacy requestLog storage, sharing the lazy database provider."""

from datetime import UTC, datetime, timedelta

from bson.int64 import Int64

from video_serverless.ports.request_journal import RequestJournalEntry


class JournalMappingError(ValueError):
    """Fixed diagnostic for metadata outside the verified storage contract."""


class MongoRequestJournal:
    def __init__(self, database_provider):
        self._database_provider = database_provider

    def save(self, entry: RequestJournalEntry) -> None:
        metadata = entry.metadata
        headers = {}
        seen = set()
        for name, value in metadata.headers:
            # Servlet header enumeration retains the first spelling/value, case insensitively.
            if name.lower() in seen:
                continue
            if "." in name:
                raise JournalMappingError("Dotted header storage is not verified")
            seen.add(name.lower())
            headers[name] = value
        request = {"url": metadata.url, "path": metadata.path, "method": metadata.method,
                   "headerMap": headers, "ip": metadata.remote_addr}
        if metadata.raw_query is not None:
            request["queryString"] = metadata.raw_query
        agent = metadata.first_header("User-Agent")
        if agent is not None:
            request["userAgent"] = agent
        epoch = datetime(1970, 1, 1, tzinfo=UTC)
        document = {"requestPath": metadata.path, "request": request,
                    "response": {"httpStatus": entry.http_status},
                    "startTime": epoch + timedelta(milliseconds=entry.started_at_ms),
                    "endTime": epoch + timedelta(milliseconds=entry.ended_at_ms),
                    "timeCost": Int64(entry.time_cost_ms)}
        # New entry has no existing ID: one insert, no application retry or business replay.
        # Keep legacy raw header/query persistence; no real credentials belong in test fixtures.
        self._database_provider().get_collection("requestLog").insert_one(document)
