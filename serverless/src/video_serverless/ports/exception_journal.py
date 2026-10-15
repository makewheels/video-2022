"""Legacy exception-log values; framework and database exceptions stay outside business."""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Protocol


@dataclass(frozen=True)
class ExceptionEntry:
    created_at: datetime
    exception_message: str | None = field(repr=False)
    stack_trace: str = field(repr=False)
    system_error_code: int | None = None
    system_error_message: str | None = None


class ExceptionJournal(Protocol):
    def save(self, entry: ExceptionEntry) -> None: ...
