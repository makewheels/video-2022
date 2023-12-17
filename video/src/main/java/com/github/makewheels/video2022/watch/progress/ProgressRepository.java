package com.github.makewheels.video2022.watch.progress;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.annotation.Resource;

@Repository
public class ProgressRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Progress getProgress(String videoId, String viewerId, String clientId) {
        Assert.isTrue(viewerId != null || clientId != null,
                "viewerId和clientId不能同时为空");
        Criteria criteria = Criteria.where("videoId").is(videoId);
        if (viewerId != null) {
            criteria.and("viewerId").is(viewerId);
        } else {
            criteria.and("clientId").is(clientId);
        }
        return mongoTemplate.findOne(new Query(criteria), Progress.class);
    }

}
