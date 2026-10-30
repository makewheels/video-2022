"""Original notification paging/count order and independent read-state mutations."""

import math
from dataclasses import dataclass

from video_serverless.ports.notifications import NotificationStore


@dataclass(frozen=True)
class NotificationPage:
    content: tuple[dict, ...]
    total: int
    total_pages: int
    current_page: int
    page_size: int


@dataclass(frozen=True)
class Notifications:
    store: NotificationStore

    def page(self, user_id: str | None, page: int, page_size: int) -> NotificationPage:
        size = min(page_size, 50)
        skip = (page * size + 2**31) % 2**32 - 2**31
        content = self.store.page(user_id, skip, size)
        total = self.store.count(user_id)
        pages = (0 if total == 0 else 2**31 - 1) if size == 0 else min(
            2**31 - 1, max(-(2**31), math.ceil(float(total) / size)))
        return NotificationPage(content, total, pages, page, size)

    def unread(self, user_id: str | None) -> int:
        return (self.store.count_unread(user_id) + 2**31) % 2**32 - 2**31

    def mark_read(self, user_id: str | None, notification_id: str | None) -> None:
        self.store.mark_read(user_id, notification_id)

    def mark_all_read(self, user_id: str | None) -> None:
        self.store.mark_all_read(user_id)
