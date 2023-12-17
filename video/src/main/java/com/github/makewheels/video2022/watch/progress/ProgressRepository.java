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

    public Progress getProgress(String videoId, String viewerId, String clientId) {
        Criteria criteria = Criteria.where("videoId").is(videoId);
        if (viewerId != null) {
            criteria.and("viewerId").is(viewerId);
        }
        if (clientId != null) {
            criteria.and("clientId").is(clientId);
        }
        return mongoTemplate.findOne(new Query(criteria), Progress.class);
    }

}
