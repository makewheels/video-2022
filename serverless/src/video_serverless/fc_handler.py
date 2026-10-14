"""Entry point: video_serverless.fc_handler.handler (FC built-in Python)."""

from video_serverless.application import dispatch
from video_serverless.http import from_fc


def handler(event, context):
    return dispatch(from_fc(event)).fc()
