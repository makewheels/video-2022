package com.github.makewheels.video2022.playlist;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.check.CheckService;
import com.github.makewheels.video2022.playlist.bean.IdBean;
import com.github.makewheels.video2022.playlist.bean.Playlist;
import com.github.makewheels.video2022.playlist.bean.PlaylistItem;
import com.github.makewheels.video2022.playlist.dto.AddVideoToPlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.CreatePlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.MoveVideoDTO;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;

@Service
@Slf4j
public class PlaylistService {
    @Resource
    private PlaylistRepository playlistRepository;
    @Resource
    private CacheService cacheService;
    @Resource
    private CheckService checkService;

    /**
     * 创建播放列表
     */
    public Playlist createPlaylist(CreatePlaylistDTO createPlaylistDTO) {
        Playlist playlist = new Playlist();
        playlist.setTitle(createPlaylistDTO.getTitle());
        playlist.setDescription(createPlaylistDTO.getDescription());
        playlist.setOwnerId(UserHolder.getUserId());
        playlistRepository.save(playlist);
        log.info("创建播放列表, playlist = {}", JSON.toJSONString(playlist));
        return playlist;
    }

    /**
     * 根据id获取播放列表
     */
    public Playlist getPlaylistById(String playlistId) {
        return cacheService.getPlaylist(playlistId);
    }

    /**
     * 把视频添加到播放列表
     */
    public void addVideoToPlaylist(AddVideoToPlaylistDTO addVideoToPlaylistDTO) {
        String playlistId = addVideoToPlaylistDTO.getPlaylistId();
        String videoId = addVideoToPlaylistDTO.getVideoId();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoNotBelongsToPlaylist(videoId, playlistId);

        // 新建playlistItem
        PlaylistItem playlistItem = new PlaylistItem();
        playlistItem.setPlaylistId(playlistId);
        playlistItem.setVideoId(videoId);
        playlistItem.setOwner(userId);
        playlistRepository.save(playlistItem);
        log.info("新增PlaylistItem = {}", JSON.toJSONString(playlistItem));

        Playlist playlist = cacheService.getPlaylist(playlistId);
        List<IdBean> idBeanList = playlist.getIdBeanList();
        if (idBeanList == null) {
            idBeanList = new LinkedList<>();
            playlist.setIdBeanList(idBeanList);
        }

        // 默认把新的item放到最前面
        IdBean idBean = new IdBean();
        idBean.setPlaylistItemId(playlistItem.getId());
        idBean.setVideoId(videoId);
        idBeanList.add(0, idBean);

        // 更新Playlist
        log.info("给Playlist添加item = {}", JSON.toJSONString(playlist));
        cacheService.updatePlaylist(playlist);
    }

    /**
     * 移动视频位置，根据mode的五种模式，移动
     * TO_INDEX            移到指定索引位置
     * //BEFORE_VIDEO     移到指定视频之前
     * //AFTER_VIDEO      移到指定视频之后
     * //TO_TOP             移到播放列表最前面
     * //TO_BOTTOM        移到播放列表最后面
     */
    public void moveVideo(MoveVideoDTO moveVideoDTO) {
        String playlistId = moveVideoDTO.getPlaylistId();
        String videoId = moveVideoDTO.getVideoId();
        String mode = moveVideoDTO.getMode();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoBelongsToPlaylist(videoId, playlistId);

        Playlist playlist = cacheService.getPlaylist(playlistId);
        List<IdBean> idBeanList = playlist.getIdBeanList();
        if (idBeanList == null) {
            idBeanList = new LinkedList<>();
            playlist.setIdBeanList(idBeanList);
        }

    }

}
