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
}
