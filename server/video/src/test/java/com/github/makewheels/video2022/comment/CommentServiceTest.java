package com.github.makewheels.video2022.comment;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.service.VideoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentServiceTest extends BaseIntegrationTest {
    @Autowired
    private CommentService commentService;
    @Autowired
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        User testUser = new User();
        testUser.setPhone("13800000020");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-comment");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        UserHolder.remove();
    }

    private String createDefaultVideo() {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename("comment-test.mp4");
        dto.setSize(1024L);
        JSONObject result = videoService.create(dto);
        return result.getString("videoId");
    }

    @Test
    void addComment_createsTopLevelComment() {
        String videoId = createDefaultVideo();
        Result<Comment> result = commentService.addComment(videoId, "测试评论", null);
        assertNotNull(result.getData());
        assertEquals("测试评论", result.getData().getContent());
        assertNull(result.getData().getParentId());

        // 验证评论数
        Result<JSONObject> countResult = commentService.getCount(videoId);
        assertEquals(1, countResult.getData().getIntValue("count"));
    }

    @Test
    void addReply_createsReplyComment() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "父评论", null).getData();
        Result<Comment> result = commentService.addComment(videoId, "回复内容", parent.getId());

        assertNotNull(result.getData());
        assertEquals(parent.getId(), result.getData().getParentId());
        assertEquals("回复内容", result.getData().getContent());

        // 验证父评论的 replyCount 增加
        List<Comment> comments = commentService.getByVideoId(videoId, 0, 20, "time").getData();
        assertEquals(1, comments.get(0).getReplyCount());
    }

    @Test
    void getByVideoId_returnsCommentsInOrder() {
        String videoId = createDefaultVideo();
        commentService.addComment(videoId, "评论1", null);
        commentService.addComment(videoId, "评论2", null);
        commentService.addComment(videoId, "评论3", null);

        List<Comment> comments = commentService.getByVideoId(videoId, 0, 20, "time").getData();
        assertEquals(3, comments.size());
        // 验证 3 条评论都存在
        assertTrue(comments.stream().anyMatch(c -> "评论1".equals(c.getContent())));
        assertTrue(comments.stream().anyMatch(c -> "评论2".equals(c.getContent())));
        assertTrue(comments.stream().anyMatch(c -> "评论3".equals(c.getContent())));
    }

    @Test
    void deleteComment_removesCommentAndUpdatesCount() {
        String videoId = createDefaultVideo();
        Comment c = commentService.addComment(videoId, "将被删除", null).getData();

        commentService.deleteComment(c.getId());

        Result<JSONObject> countResult = commentService.getCount(videoId);
        assertEquals(0, countResult.getData().getIntValue("count"));
    }

    @Test
    void deleteTopComment_alsoCascadesReplies() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "父评论", null).getData();
        commentService.addComment(videoId, "回复1", parent.getId());
        commentService.addComment(videoId, "回复2", parent.getId());

        // 总共 3 条评论
        assertEquals(3, commentService.getCount(videoId).getData().getIntValue("count"));

        commentService.deleteComment(parent.getId());

        // 全部删除
        assertEquals(0, commentService.getCount(videoId).getData().getIntValue("count"));
    }

    @Test
    void likeComment_togglesLike() {
        String videoId = createDefaultVideo();
        Comment c = commentService.addComment(videoId, "被点赞的评论", null).getData();

        // 点赞
        commentService.likeComment(c.getId());
        Comment updated = mongoTemplate.findById(c.getId(), Comment.class);
        assertEquals(1, updated.getLikeCount());

        // 再次点赞 → 取消
        commentService.likeComment(c.getId());
        updated = mongoTemplate.findById(c.getId(), Comment.class);
        assertEquals(0, updated.getLikeCount());
    }

    @Test
    void getCount_returnsCorrectCount() {
        String videoId = createDefaultVideo();

        assertEquals(0, commentService.getCount(videoId).getData().getIntValue("count"));

        commentService.addComment(videoId, "评论1", null);
        commentService.addComment(videoId, "评论2", null);

        assertEquals(2, commentService.getCount(videoId).getData().getIntValue("count"));
    }

    @Test
    void getReplies_returnsRepliesInAscOrder() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "父评论", null).getData();
        commentService.addComment(videoId, "回复A", parent.getId());
        commentService.addComment(videoId, "回复B", parent.getId());

        List<Comment> replies = commentService.getReplies(parent.getId(), 0, 20).getData();
        assertEquals(2, replies.size());
        // 按时间升序
        assertEquals("回复A", replies.get(0).getContent());
        assertEquals("回复B", replies.get(1).getContent());
    }

    // ──────────────────── 授权测试 ────────────────────

    @Test
    void deleteComment_nonAuthor_returnsError() {
        String videoId = createDefaultVideo();
        Comment comment = commentService.addComment(videoId, "别人的评论", null).getData();

        // 切换到另一个用户
        User otherUser = new User();
        otherUser.setPhone("13800000021");
        otherUser.setRegisterChannel("TEST");
        otherUser.setToken("test-token-other");
        mongoTemplate.save(otherUser);
        UserHolder.set(otherUser);

        Result<Void> result = commentService.deleteComment(comment.getId());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("无权"));

        // 评论仍然存在
        assertEquals(1, commentService.getCount(videoId).getData().getIntValue("count"));
    }

    @Test
    void deleteComment_videoUploader_canDelete() {
        // 先创建视频（当前用户是视频上传者）
        String videoId = createDefaultVideo();

        // 切换到另一个用户发表评论
        User commenter = new User();
        commenter.setPhone("13800000022");
        commenter.setRegisterChannel("TEST");
        commenter.setToken("test-token-commenter");
        mongoTemplate.save(commenter);
        UserHolder.set(commenter);

        Comment comment = commentService.addComment(videoId, "他人评论", null).getData();

        // 切回视频上传者
        User uploader = mongoTemplate.find(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("phone").is("13800000020")),
                User.class).get(0);
        UserHolder.set(uploader);

        // 视频上传者可以删除别人的评论
        Result<Void> result = commentService.deleteComment(comment.getId());
        assertEquals(0, result.getCode());
        assertEquals(0, commentService.getCount(videoId).getData().getIntValue("count"));
    }

    @Test
    void deleteComment_nonExistent_returnsError() {
        Result<Void> result = commentService.deleteComment("nonexistent_id_12345");
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("不存在"));
    }

    // ──────────────────── 级联删除测试 ────────────────────

    @Test
    void deleteTopComment_cleansUpCommentLikes() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "被点赞的父评论", null).getData();
        Comment reply = commentService.addComment(videoId, "被点赞的回复", parent.getId()).getData();

        // 对父评论和回复都点赞
        commentService.likeComment(parent.getId());
        commentService.likeComment(reply.getId());

        // 验证 CommentLike 记录存在
        long likesBefore = mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(), CommentLike.class);
        assertEquals(2, likesBefore);

        // 删除父评论（应级联删除回复和所有 CommentLike）
        commentService.deleteComment(parent.getId());

        long likesAfter = mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(), CommentLike.class);
        assertEquals(0, likesAfter, "所有 CommentLike 应被级联删除");
    }

    @Test
    void deleteChildComment_decreasesParentReplyCount() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "父评论", null).getData();
        Comment reply = commentService.addComment(videoId, "将被删除的回复", parent.getId()).getData();

        // 验证 replyCount 为 1
        Comment parentBefore = mongoTemplate.findById(parent.getId(), Comment.class);
        assertEquals(1, parentBefore.getReplyCount());

        // 删除子评论
        commentService.deleteComment(reply.getId());

        // 验证 replyCount 减为 0
        Comment parentAfter = mongoTemplate.findById(parent.getId(), Comment.class);
        assertEquals(0, parentAfter.getReplyCount());

        // 总评论数减少 1（父评论仍在）
        assertEquals(1, commentService.getCount(videoId).getData().getIntValue("count"));
    }

    // ──────────────────── 回复链测试 ────────────────────

    @Test
    void addReply_toReply_pointsToTopParent() {
        String videoId = createDefaultVideo();
        Comment topComment = commentService.addComment(videoId, "顶级评论", null).getData();
        Comment firstReply = commentService.addComment(videoId, "一级回复", topComment.getId()).getData();

        // 回复一级回复 → parentId 应指向顶级评论
        Comment secondReply = commentService.addComment(videoId, "二级回复", firstReply.getId()).getData();

        assertEquals(topComment.getId(), secondReply.getParentId(),
                "回复的回复应统一指向顶级评论");
        assertEquals(firstReply.getUserId(), secondReply.getReplyToUserId(),
                "replyToUserId 应指向被回复的用户");
    }

    @Test
    void addComment_nonExistentParent_returnsError() {
        String videoId = createDefaultVideo();
        Result<Comment> result = commentService.addComment(videoId, "回复不存在的评论", "nonexistent_parent_id");
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("不存在"));
    }

    // ──────────────────── 排序测试 ────────────────────

    @Test
    void getByVideoId_hotSort_ordersByLikeThenTime() {
        String videoId = createDefaultVideo();
        Comment c1 = commentService.addComment(videoId, "普通评论", null).getData();
        Comment c2 = commentService.addComment(videoId, "热门评论", null).getData();

        // 给 c2 点赞使其更"热门"
        commentService.likeComment(c2.getId());

        List<Comment> comments = commentService.getByVideoId(videoId, 0, 20, "hot").getData();
        assertEquals(2, comments.size());
        assertEquals("热门评论", comments.get(0).getContent(), "hot 排序应将点赞数多的排在前面");
    }

    // ──────────────────── 分页测试 ────────────────────

    @Test
    void getByVideoId_pagination_works() {
        String videoId = createDefaultVideo();
        for (int i = 0; i < 5; i++) {
            commentService.addComment(videoId, "评论" + i, null);
        }

        // 第一页取 2 条
        List<Comment> page1 = commentService.getByVideoId(videoId, 0, 2, "time").getData();
        assertEquals(2, page1.size());

        // 第二页取 2 条
        List<Comment> page2 = commentService.getByVideoId(videoId, 2, 2, "time").getData();
        assertEquals(2, page2.size());

        // 第三页取剩余
        List<Comment> page3 = commentService.getByVideoId(videoId, 4, 2, "time").getData();
        assertEquals(1, page3.size());
    }

    @Test
    void getByVideoIdPaginated_returnsMetadata() {
        String videoId = createDefaultVideo();
        for (int i = 0; i < 5; i++) {
            commentService.addComment(videoId, "评论" + i, null);
        }

        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 0, 2, "createTime").getData();
        assertEquals(2, page.getList().size());
        assertEquals(5, page.getTotal());
        assertEquals(3, page.getTotalPages());
        assertEquals(0, page.getCurrentPage());
        assertEquals(2, page.getPageSize());
    }

    @Test
    void getByVideoIdPaginated_secondPage() {
        String videoId = createDefaultVideo();
        for (int i = 0; i < 5; i++) {
            commentService.addComment(videoId, "评论" + i, null);
        }

        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 1, 2, "createTime").getData();
        assertEquals(2, page.getList().size());
        assertEquals(5, page.getTotal());
        assertEquals(1, page.getCurrentPage());
    }

    @Test
    void getByVideoIdPaginated_lastPage() {
        String videoId = createDefaultVideo();
        for (int i = 0; i < 5; i++) {
            commentService.addComment(videoId, "评论" + i, null);
        }

        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 2, 2, "createTime").getData();
        assertEquals(1, page.getList().size());
        assertEquals(5, page.getTotal());
        assertEquals(2, page.getCurrentPage());
        assertEquals(3, page.getTotalPages());
    }

    @Test
    void getByVideoIdPaginated_emptyPage() {
        String videoId = createDefaultVideo();
        // 没有评论
        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 0, 20, "createTime").getData();
        assertTrue(page.getList().isEmpty());
        assertEquals(0, page.getTotal());
        assertEquals(0, page.getTotalPages());
        assertEquals(0, page.getCurrentPage());
    }

    @Test
    void getByVideoIdPaginated_beyondLastPage() {
        String videoId = createDefaultVideo();
        for (int i = 0; i < 3; i++) {
            commentService.addComment(videoId, "评论" + i, null);
        }

        // 请求超过最后一页
        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 5, 2, "createTime").getData();
        assertTrue(page.getList().isEmpty());
        assertEquals(3, page.getTotal());
    }

    @Test
    void getByVideoIdPaginated_sortByLikeCount() {
        String videoId = createDefaultVideo();
        Comment c1 = commentService.addComment(videoId, "普通评论", null).getData();
        Comment c2 = commentService.addComment(videoId, "热门评论", null).getData();
        commentService.likeComment(c2.getId());

        CommentPageVO page = commentService.getByVideoIdPaginated(videoId, 0, 20, "likeCount").getData();
        assertEquals(2, page.getList().size());
        assertEquals("热门评论", page.getList().get(0).getContent());
    }

    @Test
    void getReplies_pagination_works() {
        String videoId = createDefaultVideo();
        Comment parent = commentService.addComment(videoId, "父评论", null).getData();
        for (int i = 0; i < 5; i++) {
            commentService.addComment(videoId, "回复" + i, parent.getId());
        }

        List<Comment> page1 = commentService.getReplies(parent.getId(), 0, 3).getData();
        assertEquals(3, page1.size());

        List<Comment> page2 = commentService.getReplies(parent.getId(), 3, 3).getData();
        assertEquals(2, page2.size());
    }

    // ──────────────────── getUserLikes 测试 ────────────────────

    @Test
    void getUserLikes_returnsBatchLikeStatus() {
        String videoId = createDefaultVideo();
        Comment c1 = commentService.addComment(videoId, "评论1", null).getData();
        Comment c2 = commentService.addComment(videoId, "评论2", null).getData();
        Comment c3 = commentService.addComment(videoId, "评论3", null).getData();

        // 只点赞 c1 和 c3
        commentService.likeComment(c1.getId());
        commentService.likeComment(c3.getId());

        String userId = UserHolder.getUserId();
        List<CommentLike> likes = commentService.getUserLikes(userId,
                java.util.Arrays.asList(c1.getId(), c2.getId(), c3.getId()));

        assertEquals(2, likes.size());
        assertTrue(likes.stream().anyMatch(l -> l.getCommentId().equals(c1.getId())));
        assertTrue(likes.stream().anyMatch(l -> l.getCommentId().equals(c3.getId())));
    }

    @Test
    void getUserLikes_emptyWhenNoLikes() {
        String videoId = createDefaultVideo();
        Comment c = commentService.addComment(videoId, "未被点赞", null).getData();

        String userId = UserHolder.getUserId();
        List<CommentLike> likes = commentService.getUserLikes(userId, java.util.Arrays.asList(c.getId()));
        assertTrue(likes.isEmpty());
    }
}
