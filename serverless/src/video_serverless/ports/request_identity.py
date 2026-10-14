"""Ordinary request-scoped metadata and identity values; no provider or thread-local state."""

from dataclasses import dataclass, field

from video_serverless.ports.users import UserSnapshot


@dataclass(frozen=True)
class RequestMetadata:
    method: str
    path: str
    url: str = field(repr=False)
    remote_addr: str
    headers: tuple[tuple[str, str], ...] = field(default=(), repr=False)
    parameters: tuple[tuple[str, str], ...] = field(default=(), repr=False)
    raw_query: str | None = field(default=None, repr=False)

    def __post_init__(self):
        # Snapshot caller-owned sequences; frozen alone would not protect a supplied list.
        object.__setattr__(self, "headers", tuple((name, value) for name, value in self.headers))
        object.__setattr__(self, "parameters", tuple((name, value) for name, value in self.parameters))

    def first_header(self, name: str) -> str | None:
        return next((value for key, value in self.headers if key.lower() == name.lower()), None)

    def first_parameter(self, name: str) -> str | None:
        return next((value for key, value in self.parameters if key == name), None)


@dataclass(frozen=True)
class ResolvedIdentity:
    token: str | None = field(repr=False)
    user: UserSnapshot | None = field(repr=False)

    @property
    def user_id(self) -> str | None:
        return None if self.user is None else self.user.id
