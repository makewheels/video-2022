package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.comment.Comment;
import com.github.makewheels.video2022.comment.CommentLike;
import com.github.makewheels.video2022.comment.CommentService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.PlaylistService;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import com.github.makewheels.video2022.video.like.LikeType;
import com.github.makewheels.video2022.video.like.VideoLike;
import com.github.makewheels.video2022.video.like.VideoLikeService;
import com.github.makewheels.video2022.video.service.VideoDeleteService;
import com.github.makewheels.video2022.video.service.VideoService;
import com.github.makewheels.video2022.watch.play.WatchLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VideoDeleteService}.
 */
class VideoDeleteServiceTest extends BaseIntegrationTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoDeleteService videoDeleteService;

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private VideoLikeService videoLikeService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000099");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-delete-service");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    private JSONObject createDefaultVideo(String filename) {
        CreateVideoDTO dto = new CreateVideoDTO();
        dto.setVideoType(VideoType.USER_UPLOAD);
        dto.setRawFilename(filename);
        dto.setSize(1024L);
        return videoService.create(dto);
    }

    @Test
    void deleteVideo_removesVideoFromDatabase() {
        JSONObject createData = createDefaultVideo("delete-test.mp4");
        String videoId = createData.getString("videoId");

        // 确认视频存在
        assertNotNull(mongoTemplate.findById(videoId, Video.class));

        // 删除
        videoDeleteService.deleteVideo(videoId);

        // 验证视频已删除
        assertNull(mongoTemplate.findById(videoId, Video.class),
                "Video should be removed from database");
    }

    @Test
    void deleteVideo_removesAssociatedFiles() {
        JSONObject createData = createDefaultVideo("delete-file-test.mp4");
        String videoId = createData.getString("videoId");
        String fileId = createData.getString("fileId");

        // 确认文件存在
        assertNotNull(mongoTemplate.findById(fileId, File.class));

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证文件记录已删除
        assertNull(mongoTemplate.findById(fileId, File.class),
                "Associated file should be removed");
    }

    @Test
    void deleteVideo_removesFromPlaylists() {
        // 创建视频
        JSONObject createData = createDefaultVideo("playlist-delete-test.mp4");
        String videoId = createData.getString("videoId");

        // 创建播放列表并添加视频
        Playlist playlist = new Playlist();
        playlist.setOwnerId(testUser.getId());
        playlist.setTitle("Test Playlist");
        playlist.setDeleted(false);
        mongoTemplate.save(playlist);

        PlayItem playItem = new PlayItem();
        playItem.setPlaylistId(playlist.getId());
        playItem.setVideoId(videoId);
        playItem.setOwner(testUser.getId());
        mongoTemplate.save(playItem);

        // 确认 PlayItem 存在
        List<PlayItem> items = mongoTemplate.find(
                Query.query(Criteria.where("videoId").is(videoId)), PlayItem.class);
        assertFalse(items.isEmpty(), "PlayItem should exist before deletion");

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证 PlayItem 已删除
        items = mongoTemplate.find(
                Query.query(Criteria.where("videoId").is(videoId)), PlayItem.class);
        assertTrue(items.isEmpty(), "PlayItem should be removed after video deletion");
    }

    @Test
    void deleteVideo_multipleVideos_onlyDeletesTarget() {
        JSONObject video1 = createDefaultVideo("keep-this.mp4");
        JSONObject video2 = createDefaultVideo("delete-this.mp4");

        String keepId = video1.getString("videoId");
        String deleteId = video2.getString("videoId");

        // 删除第二个视频
        videoDeleteService.deleteVideo(deleteId);

        // 验证：第一个视频仍然存在
        assertNotNull(mongoTemplate.findById(keepId, Video.class),
                "Other video should not be affected");

        // 第二个视频已删除
        assertNull(mongoTemplate.findById(deleteId, Video.class),
                "Deleted video should be gone");
    }

    // ──────────────────── 新增级联删除测试 ────────────────────

    @Test
    void deleteVideo_deletesComments() {
        JSONObject createData = createDefaultVideo("comments-delete.mp4");
        String videoId = createData.getString("videoId");

        // 添加评论
        commentService.addComment(videoId, "评论1", null);
        Comment parent = commentService.addComment(videoId, "评论2", null).getData();
        commentService.addComment(videoId, "回复", parent.getId());

        // 确认评论存在
        long commentsBefore = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), Comment.class);
        assertEquals(3, commentsBefore);

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证评论已删除
        long commentsAfter = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), Comment.class);
        assertEquals(0, commentsAfter, "所有评论应被级联删除");
    }

    @Test
    void deleteVideo_deletesCommentLikes() {
        JSONObject createData = createDefaultVideo("comment-likes-delete.mp4");
        String videoId = createData.getString("videoId");

        // 添加评论并点赞
        Comment c = commentService.addComment(videoId, "将被点赞", null).getData();
        commentService.likeComment(c.getId());

        // 确认 CommentLike 存在
        long likesBefore = mongoTemplate.count(new Query(), CommentLike.class);
        assertEquals(1, likesBefore);

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证 CommentLike 已删除
        long likesAfter = mongoTemplate.count(new Query(), CommentLike.class);
        assertEquals(0, likesAfter, "CommentLike 应被级联删除");
    }

    @Test
    void deleteVideo_deletesVideoLikes() {
        JSONObject createData = createDefaultVideo("video-likes-delete.mp4");
        String videoId = createData.getString("videoId");

        // 点赞视频
        videoLikeService.react(videoId, LikeType.LIKE);

        // 确认 VideoLike 存在
        long likesBefore = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), VideoLike.class);
        assertEquals(1, likesBefore);

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证 VideoLike 已删除
        long likesAfter = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), VideoLike.class);
        assertEquals(0, likesAfter, "VideoLike 应被级联删除");
    }

    @Test
    void deleteVideo_deletesWatchLogs() {
        JSONObject createData = createDefaultVideo("watchlog-delete.mp4");
        String videoId = createData.getString("videoId");

        // 手动插入 WatchLog
        WatchLog watchLog = new WatchLog();
        watchLog.setVideoId(videoId);
        watchLog.setClientId("test-client");
        watchLog.setSessionId("test-session");
        watchLog.setCreateTime(new Date());
        mongoTemplate.save(watchLog);

        // 确认 WatchLog 存在
        long logsBefore = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), WatchLog.class);
        assertEquals(1, logsBefore);

        // 删除视频
        videoDeleteService.deleteVideo(videoId);

        // 验证 WatchLog 已删除
        long logsAfter = mongoTemplate.count(
                Query.query(Criteria.where("videoId").is(videoId)), WatchLog.class);
        assertEquals(0, logsAfter, "WatchLog 应被级联删除");
    }
}
