"""Search result arithmetic and legacy category constants, with no new bounds or validation."""

import math
from dataclasses import dataclass

from video_serverless.business.public_videos import PublicVideos, VideoCard
from video_serverless.ports.video_search import VideoSearchReads

CATEGORIES = ("音乐", "游戏", "教育", "科技", "生活", "娱乐", "新闻", "体育",
              "动漫", "美食", "旅行", "知识", "影视", "搞笑", "其他")


@dataclass(frozen=True)
class SearchPage:
    content: tuple[VideoCard, ...]
    total: int
    total_pages: int
    current_page: int
    page_size: int


@dataclass(frozen=True)
class VideoSearch:
    reads: VideoSearchReads
    cards: PublicVideos

    def search(self, keyword: str | None, category: str | None, page: int, page_size: int) -> SearchPage:
        # Java int multiplication wraps, unlike Python arbitrary-precision arithmetic.
        skip = ((page * page_size + 2**31) % 2**32) - 2**31
        sources = self.reads.page(skip, page_size, keyword, category)
        total = self.reads.count(keyword, category)
        content = self.cards.enrich(sources)
        if page_size == 0:
            pages = 0 if total == 0 else 2**31 - 1
        else:
            # Preserve ceil(double) -> int saturation; do not invent a positive page-size rule.
            pages = min(2**31 - 1, max(-(2**31), math.ceil(float(total) / page_size)))
        return SearchPage(content, total, pages, page, page_size)
