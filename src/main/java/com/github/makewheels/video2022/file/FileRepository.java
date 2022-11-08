package com.github.makewheels.video2022.file;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class FileRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public File findById(String id) {
        return mongoTemplate.findById(id, File.class);
    }

    public List<File> findByIds(List<String> ids) {
        return mongoTemplate.find(
                Query.query(Criteria.where("id").in(ids)), File.class);
    }
}
