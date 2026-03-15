package com.github.makewheels.video2022.openapi.oauth.repository;

import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

@Repository
public class DeveloperRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Developer findByEmail(String email) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("email").is(email)),
                Developer.class
        );
    }

    public Developer getById(String id) {
        return mongoTemplate.findById(id, Developer.class);
    }

    public Developer save(Developer developer) {
        return mongoTemplate.save(developer);
    }
}
