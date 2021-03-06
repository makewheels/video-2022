package com.github.makewheels.video2022.transcode;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class TranscodeRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Transcode getByJobId(String jobId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("jobId").is(jobId)), Transcode.class);
    }

    public List<Transcode> getByVideoId(String videoId) {
        return mongoTemplate.find(Query.query(Criteria.where("videoId").is(videoId)), Transcode.class);
    }
}
