package com.github.makewheels.video2022.oss.osslog;

import com.github.makewheels.video2022.oss.osslog.bean.OssAccessLog;
import com.github.makewheels.video2022.oss.osslog.bean.OssAccessLogFile;
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
                OssAccessLogFile.class);
    }


    /**
     * 一条记录的md5是否存在
     */
    public boolean isOssLogLineMd5Exists(String md5) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("md5").is(md5)),
                OssAccessLog.class);
    }

}
