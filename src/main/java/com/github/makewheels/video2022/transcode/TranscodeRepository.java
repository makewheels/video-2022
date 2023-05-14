package com.github.makewheels.video2022.transcode;

import com.github.makewheels.video2022.transcode.bean.Transcode;
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

    public Transcode getById(String id) {
        return mongoTemplate.findById(id, Transcode.class);
    }

    public List<Transcode> getByIds(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), Transcode.class);
    }

    public Transcode getByJobId(String jobId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("jobId").is(jobId)), Transcode.class);
    }

}
