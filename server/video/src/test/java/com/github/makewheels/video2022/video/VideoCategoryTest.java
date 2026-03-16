package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.video.constants.VideoCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VideoCategory}.
 */
class VideoCategoryTest {

    @Test
    void allContainsExpectedCategories() {
        List<String> all = VideoCategory.ALL;
        assertEquals(15, all.size());
        assertTrue(all.contains("音乐"));
        assertTrue(all.contains("游戏"));
        assertTrue(all.contains("教育"));
        assertTrue(all.contains("科技"));
        assertTrue(all.contains("生活"));
        assertTrue(all.contains("娱乐"));
        assertTrue(all.contains("新闻"));
        assertTrue(all.contains("体育"));
        assertTrue(all.contains("动漫"));
        assertTrue(all.contains("美食"));
        assertTrue(all.contains("旅行"));
        assertTrue(all.contains("知识"));
        assertTrue(all.contains("影视"));
        assertTrue(all.contains("搞笑"));
        assertTrue(all.contains("其他"));
    }

    @Test
    void isValid_returnsTrue_forValidCategory() {
        assertTrue(VideoCategory.isValid("音乐"));
        assertTrue(VideoCategory.isValid("游戏"));
        assertTrue(VideoCategory.isValid("其他"));
    }

    @Test
    void isValid_returnsFalse_forInvalidCategory() {
        assertFalse(VideoCategory.isValid("不存在的分类"));
        assertFalse(VideoCategory.isValid(""));
        assertFalse(VideoCategory.isValid(null));
    }

    @Test
    void allListIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> VideoCategory.ALL.add("新分类"));
    }

    @Test
    void constantsMatchAllList() {
        assertEquals(VideoCategory.MUSIC, VideoCategory.ALL.get(0));
        assertEquals(VideoCategory.GAMING, VideoCategory.ALL.get(1));
        assertEquals(VideoCategory.EDUCATION, VideoCategory.ALL.get(2));
        assertEquals(VideoCategory.OTHER, VideoCategory.ALL.get(VideoCategory.ALL.size() - 1));
    }
}
