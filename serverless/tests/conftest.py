import pytest

from video_serverless import bootstrap


@pytest.fixture(autouse=True)
def isolated_composition(monkeypatch):
    # Deployment configuration is process-scoped. Tests own isolated compositions.
    monkeypatch.setattr(bootstrap, "_application", None)
