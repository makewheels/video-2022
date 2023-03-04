package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemRequest;
import com.github.makewheels.video2022.playlist.list.request.CreatePlaylistRequest;
import com.github.makewheels.video2022.playlist.list.request.UpdatePlaylistRequest;
import com.github.makewheels.video2022.playlist.item.PlayItemService;
import com.github.makewheels.video2022.playlist.list.PlaylistService;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("playlist")
@Slf4j
public class PlaylistController {
    @Resource
    private PlaylistService playlistService;
    @Resource
    private PlayItemService playItemService;

    /**
     * 创建播放列表
     */
    @PostMapping("createPlaylist")
    public Result<Playlist> createPlaylist(@RequestBody CreatePlaylistRequest createPlaylistRequest) {
        if (StringUtils.isEmpty(createPlaylistRequest.getTitle())) {
            return Result.error("播放列表title为空");
        }
        Playlist playlist = playlistService.createPlaylist(createPlaylistRequest);
        return Result.ok(playlist, "播放列表创建成功");
    }

    /**
     * 更新播放列表
     */
    @PostMapping("updatePlaylist")
    public Result<Playlist> updatePlaylist(@RequestBody UpdatePlaylistRequest updatePlaylistRequest) {
        if (StringUtils.isEmpty(updatePlaylistRequest.getTitle())) {
            return Result.error("播放列表title为空");
        }
        Playlist playlist = playlistService.updatePlaylist(updatePlaylistRequest);
        return Result.ok(playlist, "播放列表更新成功");
    }

    /**
     * 删除播放列表
     */
    @GetMapping("deletePlaylist")
    public Result<Void> deletePlaylist(@RequestParam String playlistId) {
        playlistService.deletePlaylist(playlistId);
        return Result.ok("播放列表已删除");
    }

    /**
     * 恢复播放列表
     */
    @GetMapping("recoverPlaylist")
    public Result<Void> recoverPlaylist(@RequestParam String playlistId) {
        playlistService.recoverPlaylist(playlistId);
        return Result.ok("播放列表已恢复");
    }

    /**
     * 根据playlistId获取播放列表
     */
    @GetMapping("getPlaylistById")
    public Result<Playlist> getPlaylistById(@RequestParam String playlistId) {
        Playlist playlist = playlistService.getPlaylistById(playlistId);
        if (playlist == null) {
            return Result.error("播放列表不存在, playlistId = " + playlistId);
        }
        return Result.ok(playlist);
    }

    /**
     * 分页获取指定用户的播放列表
     */
    @GetMapping("getPlaylistByPage")
    public Result<List<Playlist>> getPlaylistByPage(
            @RequestParam String userId, @RequestParam int skip, @RequestParam int limit) {
        List<Playlist> playlists = playlistService.getPlaylistByPage(userId, skip, limit);
        return Result.ok(playlists);
    }

    /**
     * 分页获取我的播放列表
     */
    @GetMapping("getMyPlaylistByPage")
    public Result<List<Playlist>> getPlaylistByPage(@RequestParam int skip, @RequestParam int limit) {
        List<Playlist> playlists = playlistService.getPlaylistByPage(
                UserHolder.getUserId(), skip, limit);
        return Result.ok(playlists);
    }

    /**
     * 添加视频到播放列表
     */
    @PostMapping("addPlaylistItem")
    public Result<Void> addPlaylistItem(
            @RequestBody AddPlayItemRequest addPlayItemRequest) {
        playItemService.addVideoListToPlaylist(addPlayItemRequest);
        return Result.ok("视频已添加到播放列表");
    }

    /**
     * 把视频从播放列表移除
     */
    @PostMapping("deletePlaylistItem")
    public Result<Void> deletePlaylistItem(
            @RequestBody DeletePlayItemRequest deletePlayItemRequest) {
        playItemService.deletePlaylistItem(deletePlayItemRequest);
        return Result.ok("视频已从播放列表移除");
    }

    /**
     * 移动视频在播放列表中的位置
     */
    @PostMapping("movePlaylistItem")
    public Result<Void> movePlaylistItem(
            @RequestBody MovePlayItemRequest movePlayItemRequest) {
        playItemService.movePlaylistItem(movePlayItemRequest);
        return Result.ok("视频位置已移动");
    }

    /**
     * 获取视频所在的播放列表
     */
    @GetMapping("getPlaylistByVideoId")
    public Result<List<String>> getPlaylistByVideoId(@RequestParam String videoId) {
        List<String> playlistIdList = playlistService.getPlaylistByVideoId(videoId);
        return Result.ok(playlistIdList);
    }
}
