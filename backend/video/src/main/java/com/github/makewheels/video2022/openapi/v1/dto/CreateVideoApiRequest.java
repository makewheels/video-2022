package com.github.makewheels.video2022.openapi.v1.dto;

import lombok.Data;

@Data
public class CreateVideoApiRequest {
    private String rawFilename;
    private Long size;
    private String videoType;
    private String ttl;
}
