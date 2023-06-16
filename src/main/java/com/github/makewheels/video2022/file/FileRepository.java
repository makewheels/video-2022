package com.github.makewheels.video2022.file;

import com.github.makewheels.video2022.file.bean.File;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
        return file != null ? file.getUploaderId() : null;
    }

    /**
     * 根据md5查文件
     */
    public File getByMd5(String md5) {
        Query query = Query.query(Criteria.where("md5").is(md5)
                // 如果文件已删除，重新上传保留文件
                .and("deleted").is(false));
        return mongoTemplate.findOne(query, File.class);
    }

    /**
     * 更新md5
     */
    public UpdateResult updateMd5(String id, String md5) {
        Query query = Query.query(Criteria.where("id").is(id));
        Update update = new Update().set("md5", md5);
        return mongoTemplate.updateFirst(query, update, File.class);
    }
}
