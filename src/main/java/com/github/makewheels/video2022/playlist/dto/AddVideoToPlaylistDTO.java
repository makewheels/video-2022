package com.github.makewheels.video2022.playlist.dto;

import lombok.Data;

@Data
public class AddVideoToPlaylistDTO {
    private String playlistId;
    private String videoId;
}
