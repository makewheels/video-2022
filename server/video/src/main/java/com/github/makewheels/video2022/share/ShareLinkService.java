package com.github.makewheels.video2022.share;

import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class ShareLinkService {
    @Resource
    private ShareLinkRepository shareLinkRepository;

    @Resource
    private VideoRepository videoRepository;

    private String generateShortCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public Result<ShareLink> createShareLink(String videoId) {
        String userId = UserHolder.getUserId();

        // Check if video exists
        Video video = videoRepository.getById(videoId);
        if (video == null) {
            return Result.error(ErrorCode.VIDEO_NOT_EXIST);
        }

        // Reuse existing share link for same user + video
        ShareLink existing = shareLinkRepository.getByVideoIdAndCreatedBy(videoId, userId);
        if (existing != null) {
            return Result.ok(existing);
        }

        ShareLink shareLink = new ShareLink();
        shareLink.setVideoId(videoId);
        shareLink.setShortCode(generateShortCode());
        shareLink.setCreatedBy(userId);
        shareLinkRepository.save(shareLink);

        log.info("Created share link: {} for video: {}", shareLink.getShortCode(), videoId);
        return Result.ok(shareLink);
    }

    public ShareLink resolveShortCode(String shortCode, String referrer) {
        ShareLink shareLink = shareLinkRepository.getByShortCode(shortCode);
        if (shareLink == null) {
            return null;
        }
        shareLinkRepository.incrementClickCount(shortCode, referrer);
        return shareLink;
    }

    public Result<ShareLink> getStats(String shortCode) {
        ShareLink shareLink = shareLinkRepository.getByShortCode(shortCode);
        if (shareLink == null) {
            return Result.error("分享链接不存在");
        }
        return Result.ok(shareLink);
    }
}
