"""Opt-in subscription mutations with legacy query binding, identity and request journals."""

from dataclasses import dataclass

from video_serverless.business.subscription_writes import SubscriptionWrites
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, _parameter

SUBSCRIPTION_WRITE_METHODS = dict.fromkeys(("/subscription/subscribe", "/subscription/unsubscribe"), "GET")


@dataclass(frozen=True)
class SubscriptionWriteApplication:
    writes: SubscriptionWrites
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in SUBSCRIPTION_WRITE_METHODS:
            return CycleResult(Response.json({"message": "Subscription write route not found"}, 404), "not_started")
        if request.method not in {"GET", "HEAD"}:
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            _parameter(metadata, "channelUserId")
            channel = ",".join(value for name, value in metadata.parameters if name == "channelUserId")
            operation = self.writes.subscribe if metadata.path == "/subscription/subscribe" else self.writes.unsubscribe
            error = operation(identity.user_id, channel)
            response = Response.json({"code": 0 if error is None else 1,
                                      "message": "success" if error is None else error, "data": None})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
