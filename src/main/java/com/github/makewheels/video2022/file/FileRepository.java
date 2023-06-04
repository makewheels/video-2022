package com.github.makewheels.video2022.file;

import com.github.makewheels.video2022.file.bean.File;
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

    public File getById(String id) {
        return mongoTemplate.findById(id, File.class);
    }

    public List<File> getByIds(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), File.class);
    }

    public boolean isFileExist(String fileId) {
        return mongoTemplate.exists(Query.query(Criteria.where("id").is(fileId)), File.class);
    }
}
