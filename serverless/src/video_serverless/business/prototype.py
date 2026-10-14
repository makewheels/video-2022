"""Existing isolated prototype use cases; not migrated production video APIs."""

from collections.abc import Callable
from dataclasses import dataclass

from video_serverless.ports.probe import ProbeReading, PublicVideoProbe


@dataclass(frozen=True)
class Prototype:
    probe: PublicVideoProbe
    clock_ms: Callable[[], int]

    def health(self) -> int:
        return self.clock_ms()

    def empty(self) -> bool:
        return True

    def read_public_video(self) -> ProbeReading:
        return self.probe.read_public_video()
