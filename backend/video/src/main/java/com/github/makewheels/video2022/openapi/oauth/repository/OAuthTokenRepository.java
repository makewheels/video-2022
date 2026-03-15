package com.github.makewheels.video2022.openapi.oauth.repository;

import com.github.makewheels.video2022.openapi.oauth.entity.OAuthToken;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

@Repository
public class OAuthTokenRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public OAuthToken findByAccessToken(String accessToken) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("accessToken").is(accessToken)),
                OAuthToken.class
        );
    }

    public OAuthToken findByRefreshToken(String refreshToken) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("refreshToken").is(refreshToken)),
                OAuthToken.class
        );
    }

    public OAuthToken save(OAuthToken token) {
        return mongoTemplate.save(token);
    }

    public void deleteByAccessToken(String accessToken) {
        mongoTemplate.remove(
                Query.query(Criteria.where("accessToken").is(accessToken)),
                OAuthToken.class
        );
    }
}
