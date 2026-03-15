package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.openapi.v1.dto.AddPlaylistItemApiRequest;
import com.github.makewheels.video2022.openapi.v1.dto.CreatePlaylistApiRequest;
import com.github.makewheels.video2022.openapi.v1.dto.UpdatePlaylistApiRequest;
import com.github.makewheels.video2022.playlist.item.PlayItemService;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.list.PlaylistService;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.playlist.list.request.CreatePlaylistRequest;
import com.github.makewheels.video2022.playlist.list.request.UpdatePlaylistRequest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Tag(name = "Playlists", description = "播放列表管理")
@RestController
@RequestMapping("/api/v1/playlists")
@Slf4j
public class ApiPlaylistController {
    @Resource
    private ApiAuthHelper apiAuthHelper;
    @Resource
    private PlaylistService playlistService;
    @Resource
    private PlayItemService playItemService;
    @Resource
    private CheckService checkService;

    @Operation(summary = "创建播放列表")
    @PostMapping
    public Result<Playlist> createPlaylist(@RequestBody CreatePlaylistApiRequest request) {
        try {
            apiAuthHelper.setupUserContext();
            CreatePlaylistRequest serviceRequest = new CreatePlaylistRequest();
            serviceRequest.setTitle(request.getTitle());
            serviceRequest.setDescription(request.getDescription());
            Playlist playlist = playlistService.createPlaylist(serviceRequest);
            return Result.ok(playlist);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "获取播放列表")
    @GetMapping
    public Result<List<Playlist>> listPlaylists(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = apiAuthHelper.requireUserId();
        int skip = (page - 1) * size;
        int limit = Math.min(size, 100);
        List<Playlist> playlists = playlistService.getPlaylistByPage(userId, skip, limit);
        return Result.ok(playlists);
    }

    @Operation(summary = "获取播放列表详情")
    @GetMapping("/{id}")
    public Result<Playlist> getPlaylist(@PathVariable String id) {
        Playlist playlist = playlistService.getPlaylistById(id, true);
        return Result.ok(playlist);
    }

    @Operation(summary = "更新播放列表")
    @PatchMapping("/{id}")
    public Result<Playlist> updatePlaylist(@PathVariable String id,
                                           @RequestBody UpdatePlaylistApiRequest request) {
        try {
            apiAuthHelper.setupUserContext();
            UpdatePlaylistRequest serviceRequest = new UpdatePlaylistRequest();
            serviceRequest.setPlaylistId(id);
            serviceRequest.setTitle(request.getTitle());
            serviceRequest.setDescription(request.getDescription());
            // 保留原有 visibility（更新请求不含此字段时使用现有值）
            Playlist existing = playlistService.getPlaylistById(id, false);
            serviceRequest.setVisibility(existing.getVisibility());
            Playlist playlist = playlistService.updatePlaylist(serviceRequest);
            return Result.ok(playlist);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "删除播放列表")
    @DeleteMapping("/{id}")
    public Result<Void> deletePlaylist(@PathVariable String id) {
        try {
            apiAuthHelper.setupUserContext();
            playlistService.deletePlaylist(id);
            return Result.ok();
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "添加视频到播放列表")
    @PostMapping("/{id}/items")
    public Result<Playlist> addItem(@PathVariable String id,
                                    @RequestBody AddPlaylistItemApiRequest request) {
        try {
            apiAuthHelper.setupUserContext();
            AddPlayItemRequest serviceRequest = new AddPlayItemRequest();
            serviceRequest.setPlaylistId(id);
            serviceRequest.setVideoIdList(Collections.singletonList(request.getVideoId()));
            serviceRequest.setAddMode("APPEND");
            Playlist playlist = playItemService.addVideoToPlaylist(serviceRequest);
            return Result.ok(playlist);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "从播放列表移除视频")
    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> removeItem(@PathVariable String id, @PathVariable String itemId) {
        try {
            apiAuthHelper.setupUserContext();
            DeletePlayItemRequest serviceRequest = new DeletePlayItemRequest();
            serviceRequest.setPlaylistId(id);
            serviceRequest.setVideoIdList(Collections.singletonList(itemId));
            serviceRequest.setDeleteMode("BY_VIDEO_ID");
            playItemService.deletePlayItem(serviceRequest);
            return Result.ok();
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }
}
