package com.github.makewheels.video2022.openapi.v1.dto;

import lombok.Data;

@Data
public class AddCommentApiRequest {
    private String content;
    private String parentId;
}
