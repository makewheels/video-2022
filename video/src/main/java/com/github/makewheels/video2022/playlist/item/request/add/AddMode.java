package com.github.makewheels.video2022.playlist.item.request.add;

/**
 * 添加视频到播放列表的模式
 */
public interface AddMode {
    String ADD_TO_TOP = "ADD_TO_TOP";
    String ADD_TO_BOTTOM = "ADD_TO_BOTTOM";

    String[] ALL = {ADD_TO_TOP, ADD_TO_BOTTOM};
}
