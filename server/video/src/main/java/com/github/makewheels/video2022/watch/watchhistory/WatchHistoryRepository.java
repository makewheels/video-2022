package com.github.makewheels.video2022.watch.watchhistory;

import com.github.makewheels.video2022.watch.play.WatchLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class WatchHistoryRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public List<WatchLog> findByViewerId(String viewerId, int skip, int limit) {
        Query query = Query.query(Criteria.where("viewerId").is(viewerId))
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, WatchLog.class);
    }

    public long countByViewerId(String viewerId) {
        Query query = Query.query(Criteria.where("viewerId").is(viewerId));
        return mongoTemplate.count(query, WatchLog.class);
    }

    public void deleteByViewerId(String viewerId) {
        Query query = Query.query(Criteria.where("viewerId").is(viewerId));
        mongoTemplate.remove(query, WatchLog.class);
    }
}
