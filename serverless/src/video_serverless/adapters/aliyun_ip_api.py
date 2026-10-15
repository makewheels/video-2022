"""Injectable IP marketplace HTTPS adapter; no real paid calls are made by tests."""

from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener

from video_serverless.adapters.ip_json import IpMappingError, decode


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


def _https(url, headers):
    try:
        with build_opener(_NoRedirect).open(Request(url, headers=headers, method="GET"), timeout=3) as response:
            return response.status, response.read()
    except HTTPError as error:
        try:
            return error.code, error.read()
        finally:
            error.close()


class AliyunIpApi:
    def __init__(self, app_code, transport=_https):
        self._app_code = app_code
        self._transport = transport

    def lookup(self, ip):
        url = "https://ips.market.alicloudapi.com/iplocaltion?ip=" + quote(ip, safe=":")
        try:
            _, body = self._transport(url, {"Authorization": "APPCODE " + self._app_code})
            return decode(body.decode("utf-8"))
        except (URLError, TimeoutError, OSError, UnicodeError):
            raise IpMappingError("IP provider request failed") from None
