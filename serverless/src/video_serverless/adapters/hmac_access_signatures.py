"""Legacy Java-compatible HMAC-SHA256 bytes and five-minute signed-long time window."""

import hashlib
import hmac
import re

from video_serverless.ports.playback_signatures import AccessClaim


def _utf8(value):
    # Java UTF-8 combines valid surrogate pairs and replaces isolated units with '?'.
    return value.encode("utf-16-le", errors="surrogatepass").decode(
        "utf-16-le", errors="surrogatepass"
    ).encode("utf-8", errors="replace")


class HmacAccessSignatures:
    def __init__(self, secret, clock_ms):
        self._secret = secret
        self._clock_ms = clock_ms

    def sign(self, claim: AccessClaim):
        if not self._secret:
            raise RuntimeError("Failed to compute file access signature")
        payload = "\n".join(value or "" for value in (
            claim.video_id, claim.client_id, claim.session_id, claim.resolution,
            claim.file_id, claim.timestamp, claim.nonce,
        ))
        return hmac.new(_utf8(self._secret), _utf8(payload), hashlib.sha256).hexdigest()

    def verify(self, claim, signature):
        if signature is None or all(char.isspace() and char not in "\x85\u00a0\u2007\u202f" for char in signature):
            return False
        return hmac.compare_digest(_utf8(self.sign(claim)), _utf8(signature))

    def timestamp_valid(self, timestamp):
        if (timestamp is None or not re.fullmatch(r"[+-]?\d+", timestamp)
                or any(ord(char) > 0xFFFF for char in timestamp)):
            return False
        try:
            value = int(timestamp)
        except ValueError:
            return False
        if not -(2**63) <= value < 2**63:
            return False
        delta = ((self._clock_ms() - value + 2**63) % 2**64) - 2**63
        # Preserve Math.abs(long)'s MIN_VALUE behavior; no new expiry arithmetic rule.
        distance = delta if delta == -(2**63) else abs(delta)
        return distance <= 300_000
