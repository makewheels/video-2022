"""Legacy comment/reply creation, cascade deletion and toggle behavior, including partial writes."""

from dataclasses import dataclass
from datetime import datetime
from typing import Callable

from video_serverless.business.hls import _trim
from video_serverless.ports.comment_writes import CommentStore
from video_serverless.ports.users import UserSnapshot


class CommentVideoMissing(RuntimeError):
    pass


@dataclass(frozen=True)
class CommentWriteResult:
    data: dict | None = None
    error_message: str | None = None


@dataclass(frozen=True)
class CommentWrites:
    store: CommentStore
    clock: Callable[[], datetime]

    def add(self, user: UserSnapshot, video_id: str, content: str | None, parent_id: str | None) -> CommentWriteResult:
        if not self.store.video_exists(video_id):
            raise CommentVideoMissing("视频不存在, videoId = " + ("null" if video_id is None else video_id))
        if content is None or not _trim(content):
            return CommentWriteResult(error_message="评论内容不能为空")
        if sum(2 if ord(char) > 0xFFFF else 1 for char in content) > 2000:
            return CommentWriteResult(error_message="评论内容不能超过2000字")
        row = {"videoId": video_id, "userId": user.id, "userPhone": user.phone,
               "userNickname": user.nickname, "userAvatarUrl": user.avatar_url, "content": _trim(content),
               "likeCount": 0, "replyCount": 0, "createTime": self.clock(), "updateTime": self.clock(),
               "parentId": None, "replyToUserId": None, "replyToUserPhone": None, "replyToUserNickname": None}
        if parent_id is not None and parent_id != "":
            parent = self.store.comment(parent_id)
            if parent is None:
                return CommentWriteResult(error_message="父评论不存在")
            top_id = parent["id"] if parent.get("parentId") is None else parent["parentId"]
            row.update(parentId=top_id, replyToUserId=parent.get("userId"),
                       replyToUserPhone=parent.get("userPhone"), replyToUserNickname=parent.get("userNickname"))
            self.store.increment_replies(top_id, 1)
        identifier = self.store.create_comment(row)
        self.store.increment_video_comments(video_id, 1)
        return CommentWriteResult(row | {"id": identifier})

    def delete(self, user_id: str | None, comment_id: str) -> CommentWriteResult:
        comment = self.store.comment(comment_id)
        if comment is None:
            return CommentWriteResult(error_message="评论不存在")
        owner = self.store.video_owner(comment.get("videoId"))
        if comment.get("userId") is None:
            raise RuntimeError("Comment author is null")
        if comment["userId"] != user_id:
            if owner is None:
                raise RuntimeError("Video uploader is absent during comment authorization")
            if owner != user_id:
                return CommentWriteResult(error_message="无权删除此评论")
        deleted = 1
        if comment.get("parentId") is None:
            replies = self.store.all_replies(comment_id)
            for reply in replies:
                self.store.remove_likes(reply["id"])
            self.store.remove_replies(comment_id)
            deleted += len(replies)
        else:
            self.store.increment_replies(comment["parentId"], -1)
        self.store.remove_likes(comment_id)
        self.store.remove_comment(comment_id)
        self.store.increment_video_comments(comment["videoId"], -deleted)
        return CommentWriteResult()

    def like(self, user_id: str | None, comment_id: str) -> CommentWriteResult:
        existing = self.store.find_like(comment_id, user_id)
        if existing is not None:
            self.store.remove_like(existing["id"])
            self.store.increment_likes(comment_id, -1)
        else:
            self.store.create_like({"commentId": comment_id, "userId": user_id, "createTime": self.clock()})
            self.store.increment_likes(comment_id, 1)
        return CommentWriteResult()
