"""Legacy two-field replacement and Java UTF-16 length rules; authentication is outside this use case."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.ports.profile_updates import ProfileUpdateResult, ProfileWrites


def _java_length(value: str) -> int:
    return len(value.encode("utf-16-le", errors="surrogatepass")) // 2


@dataclass(frozen=True)
class ProfileUpdates:
    writes: ProfileWrites
    clock: Callable[[], datetime]

    def update(self, user_id: str, nickname: str | None, bio: str | None) -> ProfileUpdateResult:
        if nickname is not None and _java_length(nickname) > 30:
            return ProfileUpdateResult("昵称不能超过30字")
        if bio is not None and _java_length(bio) > 200:
            return ProfileUpdateResult("简介不能超过200字")
        self.writes.update_profile(user_id, nickname, bio, self.clock())
        return ProfileUpdateResult()
