"""Offline, value-free HTTP fixture comparison; never requests a live endpoint."""

import json
import math
import sys
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit

MAX_INPUT_BYTES = 5 * 1024 * 1024
MAX_DIFFERENCES = 100
VARIABLE_QUERY = {"Expires", "Signature", "x-oss-date", "x-oss-expires", "x-oss-signature"}
SIGNATURE_QUERY = {"Signature", "x-oss-signature"}
SAFE_FIELDS = {
    "status", "contentType", "body", "code", "message", "data", "list", "total",
    "id", "userId", "watchCount", "duration", "coverUrl", "watchId", "watchUrl",
    "shortUrl", "title", "description", "type", "visibility", "tags", "category",
    "uploaderName", "uploaderAvatarUrl", "uploaderId", "createTime", "createTimeString",
    "youtubePublishTime", "youtubePublishTimeString",
    "phone", "registerChannel", "updateTime", "token", "nickname", "avatarFileId",
    "avatarUrl", "bannerUrl", "bio", "subscriberCount", "videoCount",
}


def cover_identity(value):
    """Ignore only explicitly time-varying signing values, preserving their presence."""
    if not isinstance(value, str):
        return value
    try:
        url = urlsplit(value)
        if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password:
            return value
        _ = url.port  # Reject malformed ports rather than hiding invalid input.
        query = parse_qsl(url.query, keep_blank_values=True)
        signatures = [item for key, item in query if key in SIGNATURE_QUERY]
        if not signatures or not all(signatures):
            return value
        if any(not item for key, item in query if key in VARIABLE_QUERY):
            return value
        normalized = sorted((key, "<variable>" if key in VARIABLE_QUERY else item) for key, item in query)
        return url.scheme, url.netloc, url.path, normalized, url.fragment
    except ValueError:
        return value


def validate_fixture(value):
    if not isinstance(value, dict) or set(value) != {"status", "contentType", "body"}:
        raise ValueError("Invalid fixture")
    if type(value["status"]) is not int or not 100 <= value["status"] <= 599:
        raise ValueError("Invalid status")
    if not isinstance(value["contentType"], str) or not value["contentType"]:
        raise ValueError("Invalid content type")


def compare(left, right):
    validate_fixture(left)
    validate_fixture(right)
    differences = []

    def record(path, kind):
        if len(differences) < MAX_DIFFERENCES:
            differences.append({"path": path, "kind": kind})

    def visit(first, second, path, keys, depth=0):
        if depth > 100:
            raise ValueError("Fixture nesting too deep")
        if any(isinstance(value, float) and not math.isfinite(value) for value in (first, second)):
            raise ValueError("Non-finite JSON number")
        if type(first) is not type(second):
            record(path, "type")
        elif isinstance(first, dict):
            if set(first) != set(second):
                record(path, "fields")
            for key in sorted(set(first) & set(second)):
                field = key if key in SAFE_FIELDS else "<field>"
                visit(first[key], second[key], path + "." + field, (*keys, key), depth + 1)
        elif isinstance(first, list):
            if len(first) != len(second):
                record(path, "length")
            for index, (one, two) in enumerate(zip(first, second)):
                visit(one, two, f"{path}[{index}]", (*keys, "*"), depth + 1)
        elif keys == ("body", "data", "list", "*", "coverUrl"):
            if cover_identity(first) != cover_identity(second):
                record(path, "value")
        elif first != second:
            record(path, "value")

    visit(left, right, "$", ())
    # Reaching the bound is conservatively marked; no values are revealed.
    return {"match": not differences, "differences": differences,
            "limitReached": len(differences) == MAX_DIFFERENCES}


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field")
        result[key] = value
    return result


def reject_constant(_value):
    raise ValueError("Non-JSON number")


def read_fixture(path):
    with Path(path).open("rb") as stream:
        raw = stream.read(MAX_INPUT_BYTES + 1)
    if len(raw) > MAX_INPUT_BYTES:
        raise ValueError("Fixture too large")
    return json.loads(raw, object_pairs_hook=strict_object, parse_constant=reject_constant)


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print(json.dumps({"error": "Expected two fixture files"}))
        return 2
    try:
        result = compare(read_fixture(args[0]), read_fixture(args[1]))
    except (OSError, ValueError, RecursionError):
        # Exception text can contain a personal path, response value or secret.
        print(json.dumps({"error": "Invalid or unreadable response fixture"}))
        return 2
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result["match"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
