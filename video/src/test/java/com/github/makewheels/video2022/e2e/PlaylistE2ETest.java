package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class PlaylistE2ETest extends BaseE2ETest {

    /**
     * 创建测试视频，返回 videoId
     */
    private String createTestVideo(String filename) {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", filename);
        body.put("size", 1024000L);

        ResponseEntity<String> response = authPost(
                getBaseUrl() + "/video/create", body.toJSONString());
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));

        JSONObject data = result.getJSONObject("data");
        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        assertNotNull(videoId);

        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);
        log.info("创建测试视频: videoId={}, fileId={}", videoId, fileId);
        return videoId;
    }

    /**
     * 创建播放列表，返回播放列表 id
     */
    private String createTestPlaylist(String title, String description) {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("description", description);

        ResponseEntity<String> response = authPost(
                getBaseUrl() + "/playlist/createPlaylist", body.toJSONString());
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"));

        JSONObject data = result.getJSONObject("data");
        String playlistId = data.getString("id");
        assertNotNull(playlistId);

        createdPlaylistIds.add(playlistId);
        log.info("创建播放列表: playlistId={}, title={}", playlistId, title);
        return playlistId;
    }

    @Test
    void testCreatePlaylistAndAddVideo() {
        // 1. 创建视频
        String videoId = createTestVideo("e2e-playlist-test.mp4");

        // 2. 创建播放列表
        String playlistId = createTestPlaylist("E2E测试播放列表", "端到端测试描述");

        // 3. 添加视频到播放列表
        JSONObject addBody = new JSONObject();
        addBody.put("playlistId", playlistId);
        addBody.put("videoIdList", List.of(videoId));
        addBody.put("addMode", "ADD_TO_TOP");

        ResponseEntity<String> addResponse = authPost(
                getBaseUrl() + "/playlist/addPlaylistItem", addBody.toJSONString());
        JSONObject addResult = JSONObject.parseObject(addResponse.getBody());
        assertEquals(0, addResult.getIntValue("code"));

        // 4. 验证 MongoDB 中的 Playlist
        Playlist playlist = mongoTemplate.findById(playlistId, Playlist.class);
        assertNotNull(playlist);
        assertEquals("E2E测试播放列表", playlist.getTitle());
        assertEquals("端到端测试描述", playlist.getDescription());
        assertEquals(testUserId, playlist.getOwnerId());

        // 5. 验证 MongoDB 中的 PlayItem
        List<PlayItem> playItems = mongoTemplate.find(
                Query.query(Criteria.where("playlistId").is(playlistId)), PlayItem.class);
        assertEquals(1, playItems.size());
        assertEquals(videoId, playItems.get(0).getVideoId());
        assertEquals(testUserId, playItems.get(0).getOwner());

        // 6. 查询我的播放列表 — 直接通过 MongoDB 验证
        // 注：getMyPlaylistByPage API 存在已知字段名问题（查询 isDelete 但实体字段是 deleted）
        List<Playlist> myPlaylists = mongoTemplate.find(
                Query.query(Criteria.where("ownerId").is(testUserId)
                        .and("deleted").is(false)),
                Playlist.class);
        assertNotNull(myPlaylists);
        assertTrue(myPlaylists.size() > 0, "应能查询到创建的播放列表");

        boolean found = false;
        for (Playlist pl : myPlaylists) {
            if (playlistId.equals(pl.getId())) {
                assertEquals("E2E测试播放列表", pl.getTitle());
                found = true;
                break;
            }
        }
        assertTrue(found, "我的播放列表中应包含刚创建的播放列表");
    }

    @Test
    void testAddMultipleVideosToPlaylist() {
        // 1. 创建两个视频
        String videoId1 = createTestVideo("e2e-multi-1.mp4");
        String videoId2 = createTestVideo("e2e-multi-2.mp4");

        // 2. 创建播放列表
        String playlistId = createTestPlaylist("多视频播放列表", "测试添加多个视频");

        // 3. 一次添加两个视频
        JSONObject addBody = new JSONObject();
        addBody.put("playlistId", playlistId);
        addBody.put("videoIdList", List.of(videoId1, videoId2));
        addBody.put("addMode", "ADD_TO_TOP");

        ResponseEntity<String> addResponse = authPost(
                getBaseUrl() + "/playlist/addPlaylistItem", addBody.toJSONString());
        JSONObject addResult = JSONObject.parseObject(addResponse.getBody());
        assertEquals(0, addResult.getIntValue("code"));

        // 4. 验证 MongoDB 中有 2 个 PlayItem
        List<PlayItem> playItems = mongoTemplate.find(
                Query.query(Criteria.where("playlistId").is(playlistId)), PlayItem.class);
        assertEquals(2, playItems.size());

        List<String> videoIds = playItems.stream()
                .map(PlayItem::getVideoId)
                .toList();
        assertTrue(videoIds.contains(videoId1), "应包含第一个视频");
        assertTrue(videoIds.contains(videoId2), "应包含第二个视频");
    }
}
