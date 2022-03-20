package com.github.makewheels.video2022.video;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class VideoRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Video getByWatchId(String watchId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("watchId").is(watchId)), Video.class);
    }

}
