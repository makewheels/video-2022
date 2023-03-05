package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class PlaylistRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public void save(Playlist playlist) {
        mongoTemplate.save(playlist);
    }

    public void save(PlayItem playItem) {
        mongoTemplate.save(playItem);
    }

    public Playlist getPlaylist(String id) {
        return mongoTemplate.findById(id, Playlist.class);
    }

    public boolean isPlaylistExist(String id) {
        return mongoTemplate.exists(Query.query(Criteria.where("id").is(id)), Playlist.class);
    }

    public PlayItem getPlayItem(Playlist playlist, String videoId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("id").is(playlist.getId())
                .and("videoId").is(videoId)), PlayItem.class);
    }

    /**
     * 更新播放列表时间
     */
    public void updateTime(Playlist playlist) {
        playlist.setUpdateTime(new Date());
        mongoTemplate.save(playlist);
    }

    /**
     * 更新播放列表项时间
     */
    public void updateTime(PlayItem playItem) {
        playItem.setUpdateTime(new Date());
        mongoTemplate.save(playItem);
    }

    /**
     * 根据id列表获取播放列表项
     */
    public List<PlayItem> getPlayItemList(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), PlayItem.class);
    }

    /**
     * 根据Playlist获取所以PlayItem
     */
    public List<PlayItem> getPlayItemList(Playlist playlist) {
        List<String> playItemIdList = playlist.getIdBeanList().stream()
                .map(IdBean::getPlayItemId).collect(Collectors.toList());
        List<PlayItem> playItemList = getPlayItemList(playItemIdList);
        //把playItemList根据playItemIdList出现顺序排序
        playItemList.sort((o1, o2) -> {
            int index1 = playItemIdList.indexOf(o1.getId());
            int index2 = playItemIdList.indexOf(o2.getId());
            return index1 - index2;
        });
        return playItemList;
    }

    /**
     * 分页获取指定userId的播放列表
     */
    public List<Playlist> getPlaylistByPage(String userId, int skip, int limit) {
        return mongoTemplate.find(
                Query.query(Criteria.where("ownerId").is(userId)
                                .and("isDeleted").is(false))
                        .skip(skip).limit(limit), Playlist.class);
    }

    /**
     * 根据videoId反向查找被哪些播放列表引用
     */
    public List<String> getPlaylistByVideoAndUser(String videoId, String userId) {
        Query query = Query.query(Criteria.where("videoId").is(videoId)
                .and("owner").is(userId));
        List<PlayItem> playItemList = mongoTemplate.find(query, PlayItem.class);
        return playItemList.stream().map(PlayItem::getPlaylistId).distinct().collect(Collectors.toList());
    }

}
