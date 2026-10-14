"""Composition root: environment/configuration, concrete providers and process reuse."""

import os
from threading import Lock
from time import time

from video_serverless.adapters.mongo import MongoPublicVideoProbe
from video_serverless.application import Application
from video_serverless.business.prototype import Prototype
from video_serverless.http import Request, Response

_application: Application | None = None
_lock = Lock()


def _build_application() -> Application:
    probe = MongoPublicVideoProbe(os.environ.get("MONGODB_URI"), os.environ.get("MONGODB_DATABASE"))
    return Application(Prototype(probe, lambda: int(time() * 1000)), os.environ.get("PROTOTYPE_TOKEN", ""))


def get_application() -> Application:
    global _application
    if _application is None:
        with _lock:
            if _application is None:
                _application = _build_application()
    return _application


def dispatch(request: Request) -> Response:
    return get_application().dispatch(request)
