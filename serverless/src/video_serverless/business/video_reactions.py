"""Legacy video reaction toggles, including their original nontransactional write order."""

from dataclasses import dataclass, replace
from datetime import datetime
from typing import Callable

from video_serverless.ports.video_reactions import VideoReaction, VideoReactionStore


class ReactionVideoMissing(RuntimeError):
    pass


@dataclass(frozen=True)
class VideoReactions:
    store: VideoReactionStore
    clock: Callable[[], datetime]

    def _require_video(self, video_id: str) -> None:
        if not self.store.video_exists(video_id):
            raise ReactionVideoMissing("视频不存在, videoId = " + video_id)

    def react(self, user_id: str | None, video_id: str, reaction_type: str) -> None:
        self._require_video(video_id)
        existing = self.store.find_reaction(video_id, user_id)
        if existing is None:
            self.store.save_reaction(VideoReaction(None, video_id, user_id, reaction_type,
                                                   self.clock(), self.clock()))
            self.store.increment_count(video_id, reaction_type, 1)
        elif existing.reaction_type is None:
            raise RuntimeError("Legacy video reaction type is null")
        elif existing.reaction_type == reaction_type:
            if existing.id is None:
                raise RuntimeError("Legacy video reaction ID is null")
            self.store.remove_reaction(existing.id)
            self.store.increment_count(video_id, reaction_type, -1)
        else:
            self.store.save_reaction(replace(existing, reaction_type=reaction_type, update_time=self.clock()))
            self.store.increment_count(video_id, existing.reaction_type, -1)
            self.store.increment_count(video_id, reaction_type, 1)

    def status(self, user_id: str | None, video_id: str) -> dict:
        self._require_video(video_id)
        counts = self.store.counts(video_id)
        if counts is None:
            raise RuntimeError("Video disappeared during legacy reaction status lookup")
        likes, dislikes = counts
        existing = None if user_id is None else self.store.find_reaction(video_id, user_id)
        return {"likeCount": 0 if likes is None else likes, "dislikeCount": 0 if dislikes is None else dislikes,
                "userAction": None if existing is None else existing.reaction_type}
