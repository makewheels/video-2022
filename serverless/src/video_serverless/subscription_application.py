"""Opt-in protected subscription reads with the existing HTTP and journal behavior."""

from dataclasses import dataclass

from video_serverless.business.subscription_reads import Subscriptions
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import _integer
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.user_application import UserBindingError, _parameter

SUBSCRIPTION_READ_METHODS = dict.fromkeys(("/subscription/getStatus", "/subscription/getMySubscriptions"), "GET")


@dataclass(frozen=True)
class SubscriptionReadApplication:
    reads: Subscriptions
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in SUBSCRIPTION_READ_METHODS:
            return CycleResult(Response.json({"message": "Subscription read route not found"}, 404), "not_started")
        if request.method not in {"GET", "HEAD"}:
            return CycleResult(self.cycle.error_response(UserBindingError("Request method is not supported")),
                               "not_started")

        def handle(metadata, identity):
            if metadata.path == "/subscription/getStatus":
                _parameter(metadata, "channelUserId")
                channel = ",".join(value for name, value in metadata.parameters if name == "channelUserId")
                data = self.reads.status(identity.user_id, channel)
            else:
                data = self.reads.mine(identity.user_id, _integer(metadata, "skip", 0), _integer(metadata, "limit", 20))
            response = Response.json({"code": 0, "message": "success", "data": data})
            return Response(response.status, response.body, {"Content-Type": "application/json"})

        return self.cycle.run(request, handle, protected=True)
