package com.github.makewheels.video2022.openapi.webhook.repository;

import com.github.makewheels.video2022.openapi.webhook.entity.WebhookDelivery;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class WebhookDeliveryRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public WebhookDelivery save(WebhookDelivery delivery) {
        return mongoTemplate.save(delivery);
    }

    public List<WebhookDelivery> findByWebhookConfigId(String webhookConfigId) {
        Query query = Query.query(Criteria.where("webhookConfigId").is(webhookConfigId));
        return mongoTemplate.find(query, WebhookDelivery.class);
    }
}
