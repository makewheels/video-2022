package com.github.makewheels.video2022.video.like;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class VideoLikeService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;

    private VideoLike findByVideoAndUser(String videoId, String userId) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("videoId").is(videoId).and("userId").is(userId)),
                VideoLike.class);
    }

    /**
     * 点赞或点踩
     */
    public Result<Void> react(String videoId, String type) {
        String userId = UserHolder.getUserId();
        VideoLike existing = findByVideoAndUser(videoId, userId);

        if (existing != null) {
            if (existing.getType().equals(type)) {
                // 相同操作 → 取消
                mongoTemplate.remove(existing);
                updateCount(videoId, type, -1);
                log.info("取消{}：videoId={}, userId={}", type, videoId, userId);
            } else {
                // 切换：如 LIKE → DISLIKE
                String oldType = existing.getType();
                existing.setType(type);
                existing.setUpdateTime(new Date());
                mongoTemplate.save(existing);
                updateCount(videoId, oldType, -1);
                updateCount(videoId, type, 1);
                log.info("切换 {} → {}：videoId={}, userId={}", oldType, type, videoId, userId);
            }
        } else {
            // 新增
            VideoLike like = new VideoLike();
            like.setVideoId(videoId);
            like.setUserId(userId);
            like.setType(type);
            like.setCreateTime(new Date());
            like.setUpdateTime(new Date());
            mongoTemplate.save(like);
            updateCount(videoId, type, 1);
            log.info("新增{}：videoId={}, userId={}", type, videoId, userId);
        }
        return Result.ok();
    }

    private void updateCount(String videoId, String type, int delta) {
        String field = LikeType.LIKE.equals(type) ? "likeCount" : "dislikeCount";
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(videoId)),
                new Update().inc(field, delta),
                Video.class);
    }

    /**
     * 获取点赞状态
     */
    public Result<JSONObject> getLikeStatus(String videoId) {
        Video video = videoRepository.getById(videoId);
        JSONObject data = new JSONObject();
        data.put("likeCount", video.getLikeCount() != null ? video.getLikeCount() : 0);
        data.put("dislikeCount", video.getDislikeCount() != null ? video.getDislikeCount() : 0);

        String userId = UserHolder.getUserId();
        if (userId != null) {
            VideoLike existing = findByVideoAndUser(videoId, userId);
            data.put("userAction", existing != null ? existing.getType() : null);
        } else {
            data.put("userAction", null);
        }
        return Result.ok(data);
    }
}
