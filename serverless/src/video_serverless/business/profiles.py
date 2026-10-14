"""Legacy profile/channel reads, including historical avatar fallback; no routes or writes."""

from dataclasses import dataclass, replace

from video_serverless.ports.users import AvatarFiles, AvatarUrls, ChannelSnapshot, UserReads, UserSnapshot


@dataclass(frozen=True)
class Profiles:
    users: UserReads
    avatar_files: AvatarFiles
    avatar_urls: AvatarUrls

    def _avatar(self, user: UserSnapshot) -> str | None:
        if user.avatar_file_id:
            key = self.avatar_files.key_for_file(user.avatar_file_id)
            if key:
                return self.avatar_urls.sign(key, 24 * 60 * 60)
        return user.avatar_url

    def mine(self, authenticated_user_id: str) -> UserSnapshot | None:
        # Authentication remains the caller's responsibility; this is not an auth bypass route.
        user = self.users.find_by_id(authenticated_user_id)
        return None if user is None else replace(user, token=None, avatar_url=self._avatar(user))

    def channel(self, user_id: str) -> ChannelSnapshot | None:
        user = self.users.find_by_id(user_id)
        if user is None:
            return None
        return ChannelSnapshot(
            user_id=user.id, nickname=user.nickname, avatar_url=self._avatar(user),
            banner_url=user.banner_url, bio=user.bio,
            subscriber_count=0 if user.subscriber_count is None else user.subscriber_count,
            video_count=0 if user.video_count is None else user.video_count,
        )
