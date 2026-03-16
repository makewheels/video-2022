package com.github.makewheels.video2022.video.constants;

import java.util.List;

/**
 * 视频分类
 */
public class VideoCategory {
    public static final String MUSIC = "音乐";
    public static final String GAMING = "游戏";
    public static final String EDUCATION = "教育";
    public static final String TECH = "科技";
    public static final String LIFE = "生活";
    public static final String ENTERTAINMENT = "娱乐";
    public static final String NEWS = "新闻";
    public static final String SPORTS = "体育";
    public static final String ANIME = "动漫";
    public static final String FOOD = "美食";
    public static final String TRAVEL = "旅行";
    public static final String KNOWLEDGE = "知识";
    public static final String FILM = "影视";
    public static final String FUNNY = "搞笑";
    public static final String OTHER = "其他";

    public static final List<String> ALL = List.of(
            MUSIC, GAMING, EDUCATION, TECH, LIFE,
            ENTERTAINMENT, NEWS, SPORTS, ANIME, FOOD,
            TRAVEL, KNOWLEDGE, FILM, FUNNY, OTHER
    );

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }
}
