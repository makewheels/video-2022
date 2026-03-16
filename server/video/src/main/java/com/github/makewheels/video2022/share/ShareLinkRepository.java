package com.github.makewheels.video2022.share;

import jakarta.annotation.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ShareLinkRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public ShareLink save(ShareLink shareLink) {
        return mongoTemplate.save(shareLink);
    }

    public ShareLink getByShortCode(String shortCode) {
        Query query = Query.query(Criteria.where("shortCode").is(shortCode));
        return mongoTemplate.findOne(query, ShareLink.class);
    }

    public ShareLink getByVideoIdAndCreatedBy(String videoId, String createdBy) {
        Query query = Query.query(
                Criteria.where("videoId").is(videoId)
                        .and("createdBy").is(createdBy)
        );
        return mongoTemplate.findOne(query, ShareLink.class);
    }

    public List<ShareLink> getByVideoId(String videoId) {
        Query query = Query.query(Criteria.where("videoId").is(videoId));
        return mongoTemplate.find(query, ShareLink.class);
    }

    public void incrementClickCount(String shortCode, String referrer) {
        Query query = Query.query(Criteria.where("shortCode").is(shortCode));
        Update update = new Update()
                .inc("clickCount", 1)
                .set("lastReferrer", referrer);
        mongoTemplate.updateFirst(query, update, ShareLink.class);
    }
}
