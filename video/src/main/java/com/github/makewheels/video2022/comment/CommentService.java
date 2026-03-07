package com.github.makewheels.video2022.comment;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class CommentService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "用户";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 添加评论
     */
    public Result<Comment> addComment(String videoId, String content, String parentId) {
        User user = UserHolder.get();
        String userId = user.getId();

        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setUserPhone(user.getPhone());
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        comment.setCreateTime(new Date());
        comment.setUpdateTime(new Date());

        if (parentId != null && !parentId.isEmpty()) {
            Comment parent = mongoTemplate.findById(parentId, Comment.class);
            if (parent == null) {
                return Result.error("父评论不存在");
            }
            // 如果回复的是子评论，parentId 统一指向顶级评论
            String topParentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
            comment.setParentId(topParentId);
            comment.setReplyToUserId(parent.getUserId());
            comment.setReplyToUserPhone(parent.getUserPhone());

            // 增加顶级评论的回复数
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(topParentId)),
                    new Update().inc("replyCount", 1),
                    Comment.class);
        }

        mongoTemplate.save(comment);

        // 更新 Video 的 commentCount
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(videoId)),
                new Update().inc("commentCount", 1),
                Video.class);

        log.info("添加评论：videoId={}, userId={}, parentId={}", videoId, userId, parentId);
        return Result.ok(comment);
    }

    /**
     * 获取视频的顶级评论（分页）
     */
    public Result<List<Comment>> getByVideoId(String videoId, int skip, int limit, String sort) {
        Query query = Query.query(
                Criteria.where("videoId").is(videoId).and("parentId").is(null));

        if ("hot".equals(sort)) {
            query.with(Sort.by(Sort.Direction.DESC, "likeCount", "createTime"));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        }

        query.skip(skip).limit(limit);
        List<Comment> comments = mongoTemplate.find(query, Comment.class);
        return Result.ok(comments);
    }

    /**
     * 获取某评论的回复（分页）
     */
    public Result<List<Comment>> getReplies(String parentId, int skip, int limit) {
        Query query = Query.query(Criteria.where("parentId").is(parentId));
        query.with(Sort.by(Sort.Direction.ASC, "createTime"));
        query.skip(skip).limit(limit);
        List<Comment> replies = mongoTemplate.find(query, Comment.class);
        return Result.ok(replies);
    }

    /**
     * 删除评论
     */
    public Result<Void> deleteComment(String commentId) {
        Comment comment = mongoTemplate.findById(commentId, Comment.class);
        if (comment == null) {
            return Result.error("评论不存在");
        }

        String userId = UserHolder.getUserId();
        Video video = videoRepository.getById(comment.getVideoId());

        // 只有评论作者或视频作者可以删除
        if (!comment.getUserId().equals(userId) && !video.getUploaderId().equals(userId)) {
            return Result.error("无权删除此评论");
        }

        int deletedCount = 1;
        if (comment.getParentId() == null) {
            // 顶级评论：先查回复，再删除
            Query replyQuery = Query.query(Criteria.where("parentId").is(commentId));
            List<Comment> replies = mongoTemplate.find(replyQuery, Comment.class);
            deletedCount += replies.size();

            // 删除回复的点赞
            for (Comment reply : replies) {
                mongoTemplate.remove(
                        Query.query(Criteria.where("commentId").is(reply.getId())),
                        CommentLike.class);
            }

            // 删除回复
            mongoTemplate.remove(replyQuery, Comment.class);
        } else {
            // 子评论：减少父评论回复数
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(comment.getParentId())),
                    new Update().inc("replyCount", -1),
                    Comment.class);
        }

        // 删除评论本身的点赞
        mongoTemplate.remove(
                Query.query(Criteria.where("commentId").is(commentId)),
                CommentLike.class);
        mongoTemplate.remove(comment);

        // 更新 Video 的 commentCount
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(comment.getVideoId())),
                new Update().inc("commentCount", -deletedCount),
                Video.class);

        log.info("删除评论：commentId={}, userId={}", commentId, userId);
        return Result.ok();
    }

    /**
     * 点赞评论（toggle）
     */
    public Result<Void> likeComment(String commentId) {
        String userId = UserHolder.getUserId();
        CommentLike existing = mongoTemplate.findOne(
                Query.query(Criteria.where("commentId").is(commentId).and("userId").is(userId)),
                CommentLike.class);

        if (existing != null) {
            mongoTemplate.remove(existing);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(commentId)),
                    new Update().inc("likeCount", -1),
                    Comment.class);
            log.info("取消评论点赞：commentId={}, userId={}", commentId, userId);
        } else {
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setCreateTime(new Date());
            mongoTemplate.save(like);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(commentId)),
                    new Update().inc("likeCount", 1),
                    Comment.class);
            log.info("评论点赞：commentId={}, userId={}", commentId, userId);
        }
        return Result.ok();
    }

    /**
     * 获取评论数
     */
    public Result<JSONObject> getCount(String videoId) {
        long count = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)),
                Comment.class);
        JSONObject data = new JSONObject();
        data.put("count", count);
        return Result.ok(data);
    }

    /**
     * 批量获取用户对评论的点赞状态
     */
    public List<CommentLike> getUserLikes(String userId, List<String> commentIds) {
        return mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId)
                        .and("commentId").in(commentIds)),
                CommentLike.class);
    }
}
