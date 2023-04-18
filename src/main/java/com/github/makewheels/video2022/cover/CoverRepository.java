package com.github.makewheels.video2022.cover;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class CoverRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Cover getById(String id){
        return mongoTemplate.findById(id, Cover.class);
    }

    public Cover getByJobId(String jobId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("jobId").is(jobId)), Cover.class);
    }

    public Cover getByVideoId(String videoId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("videoId").is(videoId)), Cover.class);
    }
}
