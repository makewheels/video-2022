"""Stable ASGI entry point: uvicorn video_serverless.web:app --port 9000."""

from video_serverless.adapters.asgi import app as app
