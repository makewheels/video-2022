"""Measure real HTTP calls without treating client-observed instances as cold proof."""

import argparse
import json
import math
import os
from time import perf_counter, sleep
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    return sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)]


def measure(url: str, token: str, count: int, interval: float, timeout: float) -> dict:
    samples = []
    # Redirects must not silently change the benchmark to another host (and carry
    # the token there). The probe has no redirect semantics.
    from urllib.request import HTTPRedirectHandler, build_opener

    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener = build_opener(NoRedirect)
    for index in range(count):
        request = Request(url, headers={"X-Prototype-Token": token})
        started = perf_counter()
        status = None
        instance = None
        ordinal = None
        payload_valid = False
        try:
            with opener.open(request, timeout=timeout) as response:
                status = response.status
                instance = response.headers.get("X-Prototype-Instance")
                ordinal = response.headers.get("X-Prototype-Invocation")
                body = response.read(65_537)
                if len(body) <= 65_536:
                    payload = json.loads(body)
                    expected = "foundPublicVideo" if urlsplit(url).path.endswith("mongo-read") else "ok"
                    payload_valid = isinstance(payload, dict) and isinstance(payload.get(expected), bool)
        except HTTPError as error:
            status = error.code
            error.close()
        except (URLError, TimeoutError, ValueError, OSError):
            pass
        samples.append({
            "elapsedMs": round((perf_counter() - started) * 1000, 3),
            "status": status, "payloadValid": payload_valid, "instance": instance, "invocation": ordinal,
        })
        if interval and index + 1 < count:
            sleep(interval)
    successful = [s["elapsedMs"] for s in samples if s["status"] == 200 and s["payloadValid"]]
    return {
        "sampleCount": count,
        "successCount": len(successful),
        "errorRate": (count - len(successful)) / count,
        "p50Ms": percentile(successful, 50),
        "p95Ms": percentile(successful, 95) if len(successful) >= 20 else None,
        "maxMs": max(successful) if successful else None,
        "coldStartVerified": False,
        "note": "Invocation ordinal is process-local; FC init logs are required to prove platform cold start.",
        "samples": samples,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="Prototype empty or mongo-read URL; do not put tokens in URL")
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--interval", type=float, default=0)
    parser.add_argument("--timeout", type=float, default=10)
    args = parser.parse_args()
    target = urlsplit(args.url)
    if target.scheme not in {"http", "https"} or target.username or target.password or target.query:
        parser.error("Expected HTTP(S) URL without credentials or query")
    if target.path not in {"/_prototype/empty", "/_prototype/mongo-read"}:
        parser.error("Only the isolated prototype endpoints may be measured")
    if args.count < 1 or args.interval < 0 or args.timeout <= 0:
        parser.error("count and timeout must be positive; interval cannot be negative")
    token = os.environ.get("PROTOTYPE_TOKEN", "")
    if not token:
        parser.error("PROTOTYPE_TOKEN is required in environment")
    result = measure(args.url, token, args.count, args.interval, args.timeout)
    print(json.dumps(result, ensure_ascii=False))
    return 0 if result["errorRate"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
