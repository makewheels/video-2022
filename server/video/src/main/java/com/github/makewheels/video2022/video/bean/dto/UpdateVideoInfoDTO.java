package com.github.makewheels.video2022.video.bean.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新视频信息
 */
@Data
public class UpdateVideoInfoDTO {
    private String id;
    private String title;
    private String description;
    private String visibility;
    private List<String> tags;
    private String category;
}
