package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.constants;

/**
 * 权限，模仿YouTube
 */
public interface Visibility {
    String PUBLIC = "PUBLIC";       //全公开
    String UNLISTED = "UNLISTED";   //拿到链接能看，但是在平台搜不到
    String PRIVATE = "PRIVATE";     //需要登录自己的账号才能看

    String[] ALL = {PUBLIC, UNLISTED, PRIVATE};
}
