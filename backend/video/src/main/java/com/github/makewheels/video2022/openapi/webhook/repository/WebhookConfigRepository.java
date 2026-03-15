package com.github.makewheels.video2022.openapi.webhook.repository;

import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class WebhookConfigRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public WebhookConfig save(WebhookConfig webhookConfig) {
        return mongoTemplate.save(webhookConfig);
    }

    public void deleteById(String id) {
        Query query = Query.query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, WebhookConfig.class);
    }

    public List<WebhookConfig> findByAppId(String appId) {
        Query query = Query.query(Criteria.where("appId").is(appId));
        return mongoTemplate.find(query, WebhookConfig.class);
    }

    public List<WebhookConfig> findByAppIdAndEventsContaining(String appId, String event) {
        Query query = Query.query(
                Criteria.where("appId").is(appId)
                        .and("status").is("active")
                        .and("events").is(event)
        );
        return mongoTemplate.find(query, WebhookConfig.class);
    }
}
