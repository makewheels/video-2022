package com.github.makewheels.video2022.notification;

import jakarta.annotation.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificationRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public void save(Notification notification) {
        mongoTemplate.save(notification);
    }

    public Notification getById(String id) {
        return mongoTemplate.findById(id, Notification.class);
    }

    public List<Notification> getByToUserId(String toUserId, int skip, int limit) {
        Query query = Query.query(Criteria.where("toUserId").is(toUserId))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Notification.class);
    }

    public long countByToUserId(String toUserId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("toUserId").is(toUserId)),
                Notification.class);
    }

    public long countUnread(String toUserId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("toUserId").is(toUserId).and("read").is(false)),
                Notification.class);
    }

    public void markAsRead(String toUserId, String notificationId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(notificationId).and("toUserId").is(toUserId)),
                Update.update("read", true),
                Notification.class);
    }

    public void markAllAsRead(String toUserId) {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("toUserId").is(toUserId).and("read").is(false)),
                Update.update("read", true),
                Notification.class);
    }
}
