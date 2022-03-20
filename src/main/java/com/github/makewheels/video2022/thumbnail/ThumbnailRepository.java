package com.github.makewheels.video2022.thumbnail;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class ThumbnailRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Thumbnail getByJobId(String jobId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("jobId").is(jobId)), Thumbnail.class);
    }

    public Thumbnail getByVideoId(String videoId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("videoId").is(videoId)), Thumbnail.class);
    }
}
