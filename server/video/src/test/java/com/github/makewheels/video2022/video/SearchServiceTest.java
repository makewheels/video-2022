package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.github.makewheels.video2022.video.bean.vo.SearchResultVO;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.service.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest extends BaseIntegrationTest {

    @Autowired
    private SearchService searchService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000001");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-search-service");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    private Video createTestVideo(String title, String description, List<String> tags, String category) {
        Video video = new Video();
        video.setUploaderId(testUser.getId());
        video.setVisibility("PUBLIC");
        video.setStatus("READY");
        video.setVideoType("USER_UPLOAD");
        video.setTitle(title);
        video.setDescription(description);
        video.setTags(tags);
        video.setCategory(category);
        video.setCreateTime(new Date());
        Watch watch = new Watch();
        watch.setWatchId("w-" + System.nanoTime());
        watch.setWatchCount(0);
        video.setWatch(watch);
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setDuration(60000L);
        video.setMediaInfo(mediaInfo);
        return mongoTemplate.save(video);
    }

    @Test
    void search_byKeyword_matchesTitle() {
        createTestVideo("Java Tutorial", "Learn basics", List.of("programming"), "EDUCATION");
        createTestVideo("Python Guide", "Learn Python", List.of("programming"), "EDUCATION");
        createTestVideo("Cooking Show", "Make pasta", List.of("food"), "ENTERTAINMENT");

        Result<SearchResultVO> result = searchService.search("Java", null, 0, 20);

        assertNotNull(result.getData());
        SearchResultVO data = result.getData();
        assertEquals(1, data.getContent().size());
        assertEquals("Java Tutorial", data.getContent().get(0).getTitle());
        assertEquals(1, data.getTotal());
    }

    @Test
    void search_byKeyword_matchesDescription() {
        createTestVideo("Video One", "Learn Spring Boot framework", List.of("tech"), "EDUCATION");
        createTestVideo("Video Two", "Cooking recipes", List.of("food"), "ENTERTAINMENT");

        Result<SearchResultVO> result = searchService.search("Spring Boot", null, 0, 20);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().getContent().size());
        assertEquals("Video One", result.getData().getContent().get(0).getTitle());
    }

    @Test
    void search_byKeyword_matchesTags() {
        createTestVideo("My Video", "Some description", List.of("kubernetes", "docker"), "TECHNOLOGY");
        createTestVideo("Other Video", "Another description", List.of("cooking"), "ENTERTAINMENT");

        Result<SearchResultVO> result = searchService.search("kubernetes", null, 0, 20);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().getContent().size());
        assertEquals("My Video", result.getData().getContent().get(0).getTitle());
    }

    @Test
    void search_byKeyword_caseInsensitive() {
        createTestVideo("JAVA Tutorial", "Learn Java", List.of("java"), "EDUCATION");

        Result<SearchResultVO> result = searchService.search("java", null, 0, 20);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().getContent().size());
        assertEquals("JAVA Tutorial", result.getData().getContent().get(0).getTitle());
    }

    @Test
    void search_byCategory_filtersCorrectly() {
        createTestVideo("Video A", "Desc A", List.of("tag1"), "EDUCATION");
        createTestVideo("Video B", "Desc B", List.of("tag2"), "EDUCATION");
        createTestVideo("Video C", "Desc C", List.of("tag3"), "ENTERTAINMENT");

        Result<SearchResultVO> result = searchService.search(null, "EDUCATION", 0, 20);

        assertNotNull(result.getData());
        assertEquals(2, result.getData().getContent().size());
        assertEquals(2, result.getData().getTotal());
    }

    @Test
    void search_byKeywordAndCategory_combinesFilters() {
        createTestVideo("Java Basics", "Intro to Java", List.of("java"), "EDUCATION");
        createTestVideo("Java Gaming", "Game dev with Java", List.of("java"), "GAMING");
        createTestVideo("Python Basics", "Intro to Python", List.of("python"), "EDUCATION");

        Result<SearchResultVO> result = searchService.search("Java", "EDUCATION", 0, 20);

        assertNotNull(result.getData());
        assertEquals(1, result.getData().getContent().size());
        assertEquals("Java Basics", result.getData().getContent().get(0).getTitle());
    }

    @Test
    void search_pagination_returnsCorrectPage() {
        for (int i = 0; i < 5; i++) {
            createTestVideo("Video " + i, "Description " + i, List.of("tag"), "EDUCATION");
        }

        Result<SearchResultVO> page0 = searchService.search(null, null, 0, 2);
        assertNotNull(page0.getData());
        assertEquals(2, page0.getData().getContent().size());
        assertEquals(5, page0.getData().getTotal());
        assertEquals(3, page0.getData().getTotalPages());
        assertEquals(0, page0.getData().getCurrentPage());
        assertEquals(2, page0.getData().getPageSize());

        Result<SearchResultVO> page1 = searchService.search(null, null, 1, 2);
        assertNotNull(page1.getData());
        assertEquals(2, page1.getData().getContent().size());
        assertEquals(1, page1.getData().getCurrentPage());
    }

    @Test
    void search_emptyKeyword_returnsAllPublicVideos() {
        createTestVideo("Video A", "Desc A", List.of("tag1"), "EDUCATION");
        createTestVideo("Video B", "Desc B", List.of("tag2"), "ENTERTAINMENT");

        // Private video should be excluded
        Video privateVideo = new Video();
        privateVideo.setUploaderId(testUser.getId());
        privateVideo.setVisibility("PRIVATE");
        privateVideo.setStatus("READY");
        privateVideo.setVideoType("USER_UPLOAD");
        privateVideo.setTitle("Private Video");
        privateVideo.setCreateTime(new Date());
        Watch watch = new Watch();
        watch.setWatchId("w-private-" + System.nanoTime());
        watch.setWatchCount(0);
        privateVideo.setWatch(watch);
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.setDuration(60000L);
        privateVideo.setMediaInfo(mediaInfo);
        mongoTemplate.save(privateVideo);

        Result<SearchResultVO> result = searchService.search(null, null, 0, 20);

        assertNotNull(result.getData());
        assertEquals(2, result.getData().getContent().size());
        assertEquals(2, result.getData().getTotal());
    }

    @Test
    void search_noResults_returnsEmptyContent() {
        createTestVideo("Video A", "Desc A", List.of("tag1"), "EDUCATION");

        Result<SearchResultVO> result = searchService.search("nonexistent_keyword_xyz", null, 0, 20);

        assertNotNull(result.getData());
        assertTrue(result.getData().getContent().isEmpty());
        assertEquals(0, result.getData().getTotal());
        assertEquals(0, result.getData().getTotalPages());
    }
}
