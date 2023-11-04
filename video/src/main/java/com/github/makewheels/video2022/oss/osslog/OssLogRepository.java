package com.github.makewheels.video2022.oss.osslog;

import com.github.makewheels.video2022.oss.osslog.bean.OssLogFile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class OssLogRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * inventoryGenerationDate是否存在
     */
    public boolean isOssLogFileKeyExists(String logFileKey) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("logFileKey").is(logFileKey)),
                OssLogFile.class);
    }

}
