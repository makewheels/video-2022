"""Search category equality is deliberately not trimmed, matching the existing repository."""

from video_serverless.adapters.mongo_public_videos import MongoPublicVideos, _filter


def _search_filter(keyword, category):
    query = _filter(keyword)
    if category is not None and category.strip("".join(chr(value) for value in range(33))):
        query["category"] = category
    return query


class MongoVideoSearch(MongoPublicVideos):
    def page(self, skip, limit, keyword, category):
        return self._page(_search_filter(keyword, category), skip, limit)

    def count(self, keyword, category):
        return self._collection().count_documents(_search_filter(keyword, category))
