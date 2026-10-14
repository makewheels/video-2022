"""Legacy token lookup and public user reads; no provider, HTTP or environment access."""

from dataclasses import dataclass, replace

from video_serverless.ports.users import UserReads, UserSnapshot


@dataclass(frozen=True)
class Users:
    reads: UserReads

    def by_token(self, token: str | None) -> UserSnapshot | None:
        if token is None:
            return None
        return self.reads.find_by_token(token)

    def public_by_id(self, user_id: str) -> UserSnapshot | None:
        user = self.reads.find_by_id(user_id)
        return None if user is None else replace(user, token=None)
