package com.github.makewheels.video2022.developer.repository;

import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class DeveloperAppRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public DeveloperApp save(DeveloperApp app) {
        return mongoTemplate.save(app);
    }

    public DeveloperApp findByAppId(String appId) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("appId").is(appId)),
                DeveloperApp.class
        );
    }

    public List<DeveloperApp> findByUserId(String userId) {
        return mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId)),
                DeveloperApp.class
        );
    }

    public DeveloperApp getById(String id) {
        return mongoTemplate.findById(id, DeveloperApp.class);
    }

    public void deleteById(String id) {
        mongoTemplate.remove(
                Query.query(Criteria.where("id").is(id)),
                DeveloperApp.class
        );
    }
}
