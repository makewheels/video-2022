"""Legacy public comment paging and counting, without adding visibility or positive-size rules."""

import math
from dataclasses import dataclass

from video_serverless.ports.comment_reads import CommentReads


class CommentReadError(RuntimeError):
    pass


@dataclass(frozen=True)
class CommentPage:
    content: tuple[dict, ...]
    total: int
    total_pages: int
    current_page: int
    page_size: int


@dataclass(frozen=True)
class Comments:
    reads: CommentReads

    def _video(self, video_id):
        if not self.reads.video_exists(video_id):
            raise CommentReadError("视频不存在, videoId = " + video_id)

    def page(self, video_id: str, page: int, page_size: int, sort_by: str) -> CommentPage:
        self._video(video_id)
        size = min(page_size, 50)
        # The old service counts first, then reads the page; no fabricated atomic snapshot.
        total = self.reads.count_top(video_id)
        skip = (page * size + 2**31) % 2**32 - 2**31
        content = self.reads.top(video_id, skip, size, sort_by)
        pages = (0 if total == 0 else 2**31 - 1) if size == 0 else min(
            2**31 - 1, max(-(2**31), math.ceil(float(total) / size)))
        return CommentPage(content, total, pages, page, size)

    def replies(self, parent_id: str, skip: int, limit: int) -> tuple[dict, ...]:
        return self.reads.replies(parent_id, skip, min(limit, 50))

    def count(self, video_id: str) -> int:
        self._video(video_id)
        return self.reads.count_all(video_id)
