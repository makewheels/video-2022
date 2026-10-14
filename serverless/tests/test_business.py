from concurrent.futures import ThreadPoolExecutor
from time import sleep

from video_serverless import bootstrap
from video_serverless.application import Application
from video_serverless.business.prototype import Prototype
from video_serverless.http import Request
from video_serverless.ports.probe import ProbeReading


def test_use_case_accepts_alternative_provider_and_clock_without_http_or_database():
    class OtherProvider:
        def __init__(self, found):
            self.found = found

        def read_public_video(self):
            return ProbeReading(self.found, 1.25, 2.5)

    for found in [False, True]:
        use_case = Prototype(OtherProvider(found), lambda: 1_730_000_000_000)
        assert use_case.health() == 1_730_000_000_000
        assert use_case.empty() is True
        assert use_case.read_public_video() == ProbeReading(found, 1.25, 2.5)


def test_one_application_is_composed_even_with_simultaneous_first_requests(monkeypatch):
    builds = []
    instance = object()

    def build():
        builds.append(True)
        sleep(0.01)
        return instance

    monkeypatch.setattr(bootstrap, "_build_application", build)
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(lambda _: bootstrap.get_application(), range(32)))
    assert all(result is instance for result in results)
    assert len(builds) == 1


def test_process_configuration_is_snapshot_until_instance_replacement(monkeypatch):
    monkeypatch.setenv("PROTOTYPE_TOKEN", "first-token")
    application = bootstrap.get_application()
    monkeypatch.setenv("PROTOTYPE_TOKEN", "replacement-token")
    request = Request("GET", "/_prototype/empty", {"x-prototype-token": "first-token"})
    assert bootstrap.get_application() is application
    assert application.dispatch(request).status == 200
    assert application.dispatch(Request("GET", request.path, {"x-prototype-token": "replacement-token"})).status == 403


def test_concurrent_diagnostics_have_unique_ordinals_and_one_instance():
    class NeverReadProbe:
        def read_public_video(self):
            raise AssertionError("empty requests must not read storage")

    application = Application(Prototype(NeverReadProbe(), lambda: 0), "test-token")
    request = Request("GET", "/_prototype/empty", {"x-prototype-token": "test-token"})
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(lambda _: application.dispatch(request), range(32)))
    assert all(result.status == 200 for result in results)
    assert {int(result.headers["X-Prototype-Invocation"]) for result in results} == set(range(1, 33))
    assert len({result.headers["X-Prototype-Instance"] for result in results}) == 1
