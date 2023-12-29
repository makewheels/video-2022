package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.playlist.item.PlayItemVO;
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
        return Result.ok("播放列表删除成功");
    }

    /**
     * 恢复播放列表
     */
    @GetMapping("recoverPlaylist")
    public Result<Void> recoverPlaylist(@RequestParam String playlistId) {
        playlistService.recoverPlaylist(playlistId);
        return Result.ok("播放列表恢复成功");
    }

    /**
     * 根据playlistId获取播放列表
     */
    @GetMapping("getPlaylistById")
    public Result<Playlist> getPlaylistById(
            @RequestParam String playlistId,
            @RequestParam(defaultValue = "false") Boolean showVideoList) {
        Playlist playlist = playlistService.getPlaylistById(playlistId, showVideoList);
        return Result.ok(playlist);
    }

    /**
     * 获取播放列表item播放详情
     */
    @GetMapping("getPlayItemListDetail")
    public Result<List<PlayItemVO>> getPlayItemListDetail(@RequestParam String playlistId) {
        List<PlayItemVO> playItemVOList = playItemService.getPlayItemListDetail(playlistId);
        return Result.ok(playItemVOList);
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
        List<Playlist> playlists = playlistService.getPlaylistByPage(UserHolder.getUserId(), skip, limit);
        return Result.ok(playlists);
    }

    /**
     * 添加视频到播放列表
     */
    @PostMapping("addPlaylistItem")
    public Result<Playlist> addPlaylistItem(
            @RequestBody AddPlayItemRequest addPlayItemRequest) {
        Playlist playlist = playItemService.addVideoToPlaylist(addPlayItemRequest);
        return Result.ok(playlist, "视频已成功添加到播放列表");
    }

    /**
     * 把视频从播放列表移除
     */
    @PostMapping("deletePlaylistItem")
    public Result<Void> deletePlaylistItem(
            @RequestBody DeletePlayItemRequest deletePlayItemRequest) {
        playItemService.deletePlayItem(deletePlayItemRequest);
        return Result.ok("视频已成功从播放列表移除");
    }

    /**
     * 移动视频在播放列表中的位置
     */
    @PostMapping("movePlaylistItem")
    public Result<Void> movePlaylistItem(
            @RequestBody MovePlayItemRequest movePlayItemRequest) {
        playItemService.movePlayItem(movePlayItemRequest);
        return Result.ok("视频位置已移动");
    }

    /**
     * 反向获取视频所在的播放列表
     */
    @GetMapping("getPlaylistByVideoId")
    public Result<List<String>> getPlaylistByVideoId(@RequestParam String videoId) {
        List<String> playlistIdList = playlistService.getPlaylistByVideoId(videoId);
        return Result.ok(playlistIdList);
    }
}
