package com.github.makewheels.video2022.comment;

import lombok.Data;

import java.util.List;

@Data
public class CommentPageVO {
    private List<Comment> list;
    private long total;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
