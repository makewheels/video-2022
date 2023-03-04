package com.github.makewheels.video2022.playlist.item.request.move;

/**
 * 移动播放列表中的视频的模式
 */
public interface MoveMode {
    String TO_INDEX = "TO_INDEX";          // 移到指定索引位置
    String BEFORE_VIDEO = "BEFORE_VIDEO";  // 移到指定视频之前
    String AFTER_VIDEO = "AFTER_VIDEO";    // 移到指定视频之后
    String TO_TOP = "TO_TOP";              // 移到播放列表最前面
    String TO_BOTTOM = "TO_BOTTOM";        // 移到播放列表最后面

    String[] ALL = {TO_INDEX, BEFORE_VIDEO, AFTER_VIDEO, TO_TOP, TO_BOTTOM};
}
