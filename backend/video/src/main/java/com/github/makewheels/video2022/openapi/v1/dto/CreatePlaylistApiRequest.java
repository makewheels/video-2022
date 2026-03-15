package com.github.makewheels.video2022.openapi.v1.dto;

import lombok.Data;

@Data
public class CreatePlaylistApiRequest {
    private String title;
    private String description;
}
