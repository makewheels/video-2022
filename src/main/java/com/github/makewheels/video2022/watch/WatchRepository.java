package com.github.makewheels.video2022.watch;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class WatchRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public boolean isWatchLogExist(String videoId, String sessionId) {
        Query query = Query.query(
                Criteria.where("videoId").is(videoId)
                        .and("sessionId").is(sessionId));
        return mongoTemplate.exists(query, WatchLog.class);
    }
}
