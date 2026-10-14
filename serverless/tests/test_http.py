import base64
import json

import pytest

from video_serverless.http import Response, from_fc


def test_fc_decodes_beacon_body_and_normalized_headers():
    body = '{"exitType":"CLOSE_TAB","说明":"退出"}'.encode()
    event = {
        "rawPath": "/playback%2Fexit",
        "requestContext": {"http": {"method": "POST", "path": "/playback/exit"}},
        "headers": {"Token": "existing-token", "Content-Type": "text/plain"},
        "body": base64.b64encode(body).decode(), "isBase64Encoded": True,
        "queryParameters": {"watchId": "existing-id"},
    }
    request = from_fc(json.dumps(event).encode())
    assert request.body == body
    assert request.path == "/playback/exit"
    assert request.header("TOKEN") == "existing-token"
    assert request.query == {"watchId": "existing-id"}


def test_hls_remains_text_and_redirect_keeps_location():
    playlist = '#EXTM3U\n#EXT-X-MAP:URI="init.mp4?sign=a"\nsegment.m4s?sign=b\n'
    result = Response(200, playlist.encode(), {"Content-Type": "application/vnd.apple.mpegurl"}).fc()
    assert result["body"] == playlist
    assert result["isBase64Encoded"] is False
    redirect = Response(302, b"", {"Location": "https://oss.example/segment?sign=existing"}).fc()
    assert redirect["statusCode"] == 302
    assert redirect["headers"]["Location"].endswith("?sign=existing")


def test_binary_valid_utf8_still_uses_base64():
    result = Response(200, b"abc", {"Content-Type": "application/octet-stream"}).fc()
    assert result["isBase64Encoded"] is True
    assert base64.b64decode(result["body"]) == b"abc"


def test_invalid_base64_is_not_silently_corrupted():
    with pytest.raises(ValueError):
        from_fc({"body": "@not-base64", "isBase64Encoded": True})
