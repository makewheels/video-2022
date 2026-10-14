import json
from dataclasses import replace

import pytest
from test_user_reads import ROOT, Reads, comparison

from video_serverless.business.request_identity import IdentityResolver
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.ports.users import UserSnapshot
from video_serverless.request_cycle import RequestCycle


def metadata(path="/user/getMyProfile", token="fixture-legacy-token"):
    return RequestMetadata("GET", path, "http://fixture.invalid" + path, "192.0.2.10",
                           () if token is None else (("token", token),))


class Journal:
    def __init__(self, fail=False):
        self.entries = []
        self.fail = fail

    def save(self, entry):
        if self.fail:
            raise RuntimeError("fixture-private-journal-failure")
        self.entries.append(entry)


def cycle(reads=None, journal=None, times=(1000, 1025)):
    ticks = iter(times)
    return RequestCycle(IdentityResolver(reads or Reads()), journal or Journal(), lambda: next(ticks))


def ok(request, identity):
    return Response.json({"fixtureUserId": identity.user_id})


def test_protected_success_looks_up_twice_and_saves_synchronously_before_return():
    reads, journal = Reads(), Journal()
    events = []

    class Observed(Journal):
        def save(self, entry):
            events.append("save")
            journal.save(entry)

    def handler(request, identity):
        assert reads.calls == [("token", "fixture-legacy-token")] * 2
        events.append("handler")
        return ok(request, identity)

    result = cycle(reads, Observed()).run(metadata(), handler, protected=True)
    events.append("returned")
    assert events == ["handler", "save", "returned"]
    assert result.journal_status == "saved"
    entry = journal.entries[0]
    assert (entry.started_at_ms, entry.ended_at_ms, entry.time_cost_ms, entry.http_status) == (1000, 1025, 25, 200)
    assert entry.metadata.path == "/user/getMyProfile"


def test_protected_denial_matches_java_http_golden_and_does_not_start_journal_or_handler():
    reads, journal = Reads(), Journal()

    def forbidden(*args):
        raise AssertionError("Denied request must not invoke a handler or clock")

    result = RequestCycle(IdentityResolver(reads), journal, forbidden).run(metadata(token="fixture-wrong"),
                                                                         forbidden, protected=True)
    expected = json.loads((ROOT / "contracts/request-cycle/unauthorized.json").read_text())
    actual = {"status": result.response.status, "contentType": result.response.headers["Content-Type"],
              "body": json.loads(result.response.body)}
    assert comparison.compare(expected, actual)["match"]
    assert result.journal_status == "not_started"
    assert reads.calls == [("token", "fixture-wrong")] * 2
    assert journal.entries == []


def test_identity_failure_before_logger_returns_generic_java_error_without_journal():
    class Unavailable:
        def find_by_token(self, token):
            raise RuntimeError("fixture-private-identity-failure")

    journal = Journal()
    result = cycle(Unavailable(), journal).run(metadata(), ok, protected=True)
    assert result.journal_status == "not_started"
    assert journal.entries == []
    assert json.loads(result.response.body) == {"code": 1, "message": "未知错误", "data": None}
    assert b"fixture-private" not in result.response.body


def test_handler_failure_matches_java_error_golden_and_logs_final_http_status():
    journal = Journal()

    def broken(*args):
        raise ValueError("fixture-private-handler-failure")

    result = cycle(journal=journal).run(metadata(), broken)
    expected = json.loads((ROOT / "contracts/request-cycle/binding-error.json").read_text())
    actual = {"status": result.response.status, "contentType": result.response.headers["Content-Type"],
              "body": json.loads(result.response.body)}
    assert comparison.compare(expected, actual)["match"]
    assert result.journal_status == "saved"
    assert journal.entries[0].http_status == 200


def test_journal_failure_preserves_response_business_write_and_next_request_is_independent():
    writes = []
    journal = Journal(fail=True)
    lifecycle = cycle(journal=journal)

    def business(request, identity):
        writes.append(identity.user_id)
        return Response.text("fixture-success", 202)

    result = lifecycle.run(metadata(), business, protected=True)
    assert result.response.status == 202 and result.response.body == b"fixture-success"
    assert result.journal_status == "failed"
    assert writes == ["fixture-user-reads"]
    denied = lifecycle.run(metadata(token="fixture-wrong"), business, protected=True)
    assert denied.response.status == 401 and denied.journal_status == "not_started"
    assert writes == ["fixture-user-reads"]
    assert "fixture-private" not in repr(result)


@pytest.mark.parametrize("path", ["/file/access", "/heartbeat/add"])
def test_exact_excluded_paths_skip_journal_after_handler(path):
    journal = Journal()
    result = cycle(journal=journal).run(metadata(path), ok)
    assert result.response.status == 200 and result.journal_status == "skipped"
    assert journal.entries == []


def test_similar_path_is_not_excluded_and_clock_difference_is_not_clamped():
    journal = Journal()
    result = cycle(journal=journal, times=(1025, 1000)).run(metadata("/heartbeat/add/"), ok)
    assert result.journal_status == "saved"
    assert journal.entries[0].time_cost_ms == -25


def test_two_lookups_keep_first_identity_even_if_second_lookup_observes_a_new_user():
    class Changing:
        def __init__(self):
            self.lookups = 0

        def find_by_token(self, token):
            self.lookups += 1
            return UserSnapshot(id="fixture-first" if self.lookups == 1 else "fixture-second", token=token)

    result = cycle(reads=Changing()).run(metadata(), ok, protected=True)
    assert json.loads(result.response.body)["fixtureUserId"] == "fixture-first"


def test_domain_specific_error_mapping_is_explicitly_injected_not_replaced_by_generic_fallback():
    lifecycle = replace(cycle(), error_response=lambda error: Response.json({"code": 33}, 200))

    def domain_error(*args):
        raise ValueError("fixture-domain-failure")

    result = lifecycle.run(metadata(), domain_error)
    assert json.loads(result.response.body) == {"code": 33}
