package com.github.makewheels.video2022.cover;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class CoverRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Cover getById(String id) {
        return mongoTemplate.findById(id, Cover.class);
    }

    /**
     * 根据id批量查
     */
    public List<Cover> getByIdList(List<String> idList) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(idList)), Cover.class);
    }

    public Cover getByJobId(String jobId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("jobId").is(jobId)), Cover.class);
    }

    public Cover getByVideoId(String videoId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("videoId").is(videoId)), Cover.class);
    }
}
