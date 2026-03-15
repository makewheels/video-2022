package com.github.makewheels.video2022.etc.app;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

@Repository
public class AppVersionRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 查找某平台最新版本
     */
    public AppVersion findLatestByPlatform(String platform) {
        Query query = new Query(Criteria.where("platform").is(platform));
        query.with(Sort.by(Sort.Direction.DESC, "versionCode"));
        query.limit(1);
        return mongoTemplate.findOne(query, AppVersion.class);
    }

    public AppVersion save(AppVersion appVersion) {
        return mongoTemplate.save(appVersion);
    }
}
