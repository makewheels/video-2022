package com.github.makewheels.video2022.subscription;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class SubscriptionRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Subscription findByUserIdAndChannelUserId(String userId, String channelUserId) {
        Query query = Query.query(
                Criteria.where("userId").is(userId)
                        .and("channelUserId").is(channelUserId));
        return mongoTemplate.findOne(query, Subscription.class);
    }

    public List<Subscription> findByUserId(String userId, int skip, int limit) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .skip(skip).limit(limit)
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, Subscription.class);
    }

    public long countByChannelUserId(String channelUserId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("channelUserId").is(channelUserId)),
                Subscription.class);
    }

    public void save(Subscription subscription) {
        mongoTemplate.save(subscription);
    }

    public void delete(Subscription subscription) {
        mongoTemplate.remove(subscription);
    }
}
