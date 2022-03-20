package com.github.makewheels.video2022.video;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class VideoRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Video getByWatchId(String watchId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("watchId").is(watchId)), Video.class);
    }

    public List<Video> getVideoList(String userId, int skip, int limit) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                //根据时间降序排列
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Video.class);
    }

}
