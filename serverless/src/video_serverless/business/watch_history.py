"""Legacy per-log pagination and clearing, without new login or visibility rules."""

from dataclasses import dataclass

from video_serverless.ports.watch_history import HistoryCoverUrls, HistoryItem, HistoryPage, HistoryStore


@dataclass(frozen=True)
class WatchHistory:
    store: HistoryStore
    covers: HistoryCoverUrls

    def get(self, viewer_id: str | None, page: int = 0, page_size: int = 20) -> HistoryPage:
        skip = (page * page_size + 2**31) % 2**32 - 2**31
        rows = self.store.page(viewer_id, skip, page_size)
        total = self.store.count(viewer_id)
        ids = tuple(dict.fromkeys(row.video_id for row in rows))
        videos = {}
        for video in self.store.videos(ids):
            if video.id in videos:
                raise ValueError("Legacy video map contains duplicate IDs")
            videos[video.id] = video
        items = []
        for row in rows:
            video = videos.get(row.video_id)
            cover = None if video is None or video.cover_id is None else self.covers.signed(video.cover_id)
            items.append(HistoryItem(row.video_id, None if video is None else video.title, cover, row.watched_at))
        return HistoryPage(tuple(items), total, page, page_size)

    def clear(self, viewer_id: str | None) -> None:
        self.store.clear(viewer_id)
