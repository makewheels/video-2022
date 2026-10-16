"""Spring generic JSONObject binding followed by Java value.toString access, not Fastjson exit parsing."""

from video_serverless.java_numbers import double_text
from video_serverless.playback_json import _spring_object
from video_serverless.profile_json import _Number, _Object


def _java_value(value):
    if value is None:
        return "null"
    if isinstance(value, _Number):
        return double_text(float(value)) if any(char in value for char in ".eE") else str(int(value))
    if isinstance(value, _Object):
        return "{" + ", ".join(name + "=" + _java_value(item) for name, item in dict(value).items()) + "}"
    if isinstance(value, list):
        return "[" + ", ".join(_java_value(item) for item in value) + "]"
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def decode_comment(body, content_type):
    fields = dict(_spring_object(body, content_type))
    return tuple(None if fields.get(name) is None else _java_value(fields[name])
                 for name in ("videoId", "content", "parentId"))
