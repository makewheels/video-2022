"""One legacy request identity lookup; authentication gates and logging are not installed."""

from dataclasses import dataclass

from video_serverless.ports.request_identity import RequestMetadata, ResolvedIdentity
from video_serverless.ports.users import UserReads


def select_token(request: RequestMetadata) -> str | None:
    token = request.first_header("token")
    if token is None or token == "":
        parameter = request.first_parameter("token")
        if parameter is not None:
            token = parameter
    return token


@dataclass(frozen=True)
class IdentityResolver:
    users: UserReads

    def resolve(self, request: RequestMetadata) -> ResolvedIdentity:
        token = select_token(request)
        return ResolvedIdentity(token, None if token is None else self.users.find_by_token(token))
