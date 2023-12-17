package com.github.makewheels.video2022.watch.progress;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class ProgressRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Progress getByVideoIdAndViewerId(String videoId, String viewerId) {
        Query query = Query.query(Criteria.where("videoId").is(videoId)
                .and("viewerId").is(viewerId));
        return mongoTemplate.findOne(query, Progress.class);
    }
}
