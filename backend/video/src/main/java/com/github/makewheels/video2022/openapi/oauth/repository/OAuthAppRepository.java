package com.github.makewheels.video2022.openapi.oauth.repository;

import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class OAuthAppRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public OAuthApp findByClientId(String clientId) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("clientId").is(clientId)),
                OAuthApp.class
        );
    }

    public List<OAuthApp> findByDeveloperId(String developerId) {
        return mongoTemplate.find(
                Query.query(Criteria.where("developerId").is(developerId)),
                OAuthApp.class
        );
    }

    public OAuthApp getById(String id) {
        return mongoTemplate.findById(id, OAuthApp.class);
    }

    public OAuthApp save(OAuthApp app) {
        return mongoTemplate.save(app);
    }

    public void deleteById(String id) {
        mongoTemplate.remove(
                Query.query(Criteria.where("id").is(id)),
                OAuthApp.class
        );
    }
}
