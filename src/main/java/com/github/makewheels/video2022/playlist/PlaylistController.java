package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.playlist.bean.Playlist;
import com.github.makewheels.video2022.playlist.dto.AddVideoToPlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.CreatePlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.MoveVideoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("playlist")
@Slf4j
public class PlaylistController {
    @Resource
    private PlaylistService playlistService;

    @PostMapping("createPlaylist")
    public Result<Playlist> createPlaylist(@RequestBody CreatePlaylistDTO createPlaylistDTO) {
        Playlist playlist = playlistService.createPlaylist(createPlaylistDTO);
        return Result.ok(playlist);
    }

    @GetMapping("getPlaylistById")
    public Result<Playlist> getPlaylistById(@RequestParam String playlistId) {
        Playlist playlist = playlistService.getPlaylistById(playlistId);
        if (playlist == null) {
            log.warn("播放列表不存在, playlistId = {}", playlistId);
            return Result.error("播放列表不存在, playlistId = " + playlistId);
        }
        return Result.ok(playlist);
    }

    @PostMapping("addVideoToPlaylist")
    public Result<Void> addVideoToPlaylist(@RequestBody AddVideoToPlaylistDTO addVideoToPlaylistDTO) {
        playlistService.addVideoToPlaylist(addVideoToPlaylistDTO);
        return Result.ok();
    }

    @PostMapping("moveVideo")
    public Result<Void> moveVideo(@RequestBody MoveVideoDTO moveVideoDTO) {
        playlistService.moveVideo(moveVideoDTO);
        return Result.ok();
    }
}
