package com.github.makewheels.video2022.video.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SearchResultVO {
    private List<VideoVO> content;
    private long total;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
