package com.github.makewheels.video2022.file;

import com.github.makewheels.video2022.file.bean.TsFile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class TsFileRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public TsFile getById(String id) {
        return mongoTemplate.findById(id, TsFile.class);
    }

    public List<TsFile> getByIds(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), TsFile.class);
    }

    public String getKeyById(String id) {
        Query query = Query.query(Criteria.where("id").is(id));
        query.fields().include("key");
        TsFile tsFile = mongoTemplate.findOne(query, TsFile.class);
        return tsFile != null ? tsFile.getKey() : null;
    }

}
