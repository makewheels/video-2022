package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
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

    /**
     * 根据id列表获取播放列表项
     */
    public List<PlayItem> getPlayItemList(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), PlayItem.class);
    }

    /**
     * 分页获取指定userId的播放列表
     */
    public List<Playlist> getPlaylistByPage(String userId, int skip, int limit) {
        return mongoTemplate.find(Query.query(Criteria.where("ownerId").is(userId))
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
