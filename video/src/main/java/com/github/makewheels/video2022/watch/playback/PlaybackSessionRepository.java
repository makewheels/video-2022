package com.github.makewheels.video2022.watch.playback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaybackSessionRepository {
    @Autowired
    private MongoTemplate mongoTemplate;

    public void save(PlaybackSession session) {
        mongoTemplate.save(session);
    }

    public PlaybackSession getById(String id) {
        return mongoTemplate.findById(id, PlaybackSession.class);
    }
}
