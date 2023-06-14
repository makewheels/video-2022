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

    /**
     * 根据id查文件
     */
    public File getById(String id) {
        return mongoTemplate.findById(id, File.class);
    }

    /**
     * 根据id批量查文件
     */
    public List<File> getByIds(List<String> ids) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), File.class);
    }

    /**
     * 文件是否存在
     */
    public boolean isFileExist(String fileId) {
        return mongoTemplate.exists(Query.query(Criteria.where("id").is(fileId)), File.class);
    }

    /**
     * 根据文件id查用户id
     */
    public String getUserIdByFileId(String fileId) {
        Query query = Query.query(Criteria.where("id").is(fileId));
        query.fields().include("uploaderId");
        File file = mongoTemplate.findOne(query, File.class);
        if (file != null) {
            return file.getUploaderId();
        }
        return null;
    }
}
