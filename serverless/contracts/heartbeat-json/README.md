# Legacy heartbeat JSON snapshots

`cases.json` contains 50 synthetic UTF-8 request bodies, complete legacy HTTP
responses, and normalized Mongo heartbeat documents from
`HeartbeatJsonHttpContractTest`. Generated IDs and server createTime are excluded;
dates become epoch milliseconds and Decimal128 values retain their decimal text.

The Java test normally compares against this file. Explicit
`-Dcontract.output.dir=<absolute directory>` exports a fresh fixture for review.
Run only with a task-owned loopback Mongo database and mocked external services;
never regenerate from production. Python `test_heartbeat_http.py` compares the
same snapshots against actual ASGI and isolated Mongo writes.

These samples do not verify every Jackson date/encoding corner case, real FC
networking, or production playback. See the S09.k validation boundaries in
`docs/requirements/2026-10-14-serverless-backend/playback/heartbeat-progress.md`.
