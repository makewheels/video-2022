"""Legacy cache presence, flattening and save ordering; no application retry."""

from video_serverless.adapters.ip_json import IpMappingError, decode, encode


class CachedIpLocations:
    def __init__(self, cache, api, clock):
        self._cache = cache
        self._api = api
        self._clock = clock

    def lookup(self, ip):
        key = ip.replace(":", "_")
        cached = self._cache.find(key)
        if cached is not None:
            return decode(cached.location_json)
        outer = self._api.lookup(ip)
        if not isinstance(outer, dict) or not isinstance(outer.get("result"), dict):
            raise IpMappingError("IP response result shape is not verified")
        result = dict(outer)
        result["ip"] = ip
        result.update(outer["result"])
        result.pop("result", None)
        serialized = encode(result)
        self._cache.save(key, serialized, self._clock())
        return result
