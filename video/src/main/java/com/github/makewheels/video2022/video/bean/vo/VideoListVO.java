package com.github.makewheels.video2022.video.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoListVO {
    private List<VideoVO> list;
    private long total;
}
