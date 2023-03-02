package com.github.makewheels.video2022.playlist;

import com.github.makewheels.video2022.playlist.bean.Playlist;
import com.github.makewheels.video2022.playlist.bean.PlaylistItem;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class PlaylistRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public void save(Playlist playlist) {
        mongoTemplate.save(playlist);
    }

    public void save(PlaylistItem playlistItem) {
        mongoTemplate.save(playlistItem);
    }

    public Playlist getPlaylistById(String id) {
        return mongoTemplate.findById(id, Playlist.class);
    }

    public List<PlaylistItem> getPlaylistItemList(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), PlaylistItem.class);
    }
}
