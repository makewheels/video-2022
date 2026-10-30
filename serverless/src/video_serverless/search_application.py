"""Legacy search/category HTTP routes using the established request lifecycle."""

from dataclasses import dataclass

from video_serverless.business.video_search import CATEGORIES, VideoSearch
from video_serverless.http import Response
from video_serverless.ports.request_identity import RequestMetadata
from video_serverless.public_video_application import _integer, _string
from video_serverless.request_cycle import CycleResult, RequestCycle
from video_serverless.video_responses import video_result, video_rows


@dataclass(frozen=True)
class SearchApplication:
    search: VideoSearch
    cycle: RequestCycle

    def dispatch(self, request: RequestMetadata, body: bytes = b"") -> CycleResult:
        if request.path not in {"/search", "/category/list"}:
            return CycleResult(Response.json({"message": "Search route not found"}, 404), "not_started")

        def handle(metadata, identity):
            if metadata.method not in {"GET", "HEAD"}:
                raise ValueError("Request method is not supported")
            if metadata.path == "/category/list":
                return video_result(list(CATEGORIES))
            page = self.search.search(_string(metadata, "q"), _string(metadata, "category"),
                                      _integer(metadata, "page", 0), _integer(metadata, "pageSize", 20))
            return video_result({"content": video_rows(page.content), "total": page.total,
                                 "totalPages": page.total_pages, "currentPage": page.current_page,
                                 "pageSize": page.page_size})

        return self.cycle.run(request, handle)
