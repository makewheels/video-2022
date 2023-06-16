package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.video.bean.entity.StorageStatus;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.Watch;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class VideoRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Video getById(String id) {
        return mongoTemplate.findById(id, Video.class);
    }

    public List<Video> getByIdList(List<String> idList) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(idList)), Video.class);
    }

    public Map<String, Video> getMapByIdList(List<String> idList) {
        return getByIdList(idList).stream().collect(Collectors.toMap(Video::getId, Function.identity()));
    }

    /**
     * 更新status
     */
    public UpdateResult updateStatus(String id, String status) {
        Query query = Query.query(Criteria.where("id").is(id));
        Update update = new Update().set("status", status);
        return mongoTemplate.updateFirst(query, update, Video.class);
    }

    public boolean isVideoExist(String id) {
        return mongoTemplate.exists(Query.query(Criteria.where("id").is(id)), Video.class);
    }

    public Video getByWatchId(String watchId) {
        Query query = Query.query(Criteria.where(Watch.FIELD_NAME + ".watchId").is(watchId));
        return mongoTemplate.findOne(query, Video.class);
    }

    /**
     * 根据userId分页获取视频列表
     */
    public List<Video> getVideosByUserId(String userId, int skip, int limit) {
        Query query = Query.query(Criteria.where("uploaderId").is(userId))
                //根据时间降序排列
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Video.class);
    }

    /**
     * 增加观看次数
     */
    public void addWatchCount(String videoId) {
        Query query = Query.query(Criteria.where("id").is(videoId));
        Update update = new Update();
        update.inc(Watch.FIELD_NAME + ".watchCount");
        mongoTemplate.updateFirst(query, update, Video.class);
    }

    /**
     * 获取过期视频
     */
    public List<Video> getExpiredVideos(int skip, int limit) {
        Criteria criteria = Criteria.where(
                        StorageStatus.FIELD_NAME + ".isPermanent").is(false)
                .and(StorageStatus.FIELD_NAME + ".expireTime").lt(new Date());
        Query query = Query.query(criteria).skip(skip).limit(limit);
        return mongoTemplate.find(query, Video.class);
    }
}
