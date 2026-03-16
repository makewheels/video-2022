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

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
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
        return getByIdList(idList).stream().collect(
                Collectors.toMap(Video::getId, Function.identity()));
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

    /**
     * watchId是否存在
     */
    public boolean isWatchIdExist(String watchId) {
        return mongoTemplate.exists(Query.query(
                        Criteria.where(Watch.FIELD_NAME + ".watchId").is(watchId)),
                Video.class
        );
    }

    public Video getByWatchId(String watchId) {
        Query query = Query.query(Criteria.where(Watch.FIELD_NAME + ".watchId").is(watchId));
        return mongoTemplate.findOne(query, Video.class);
    }

    /**
     * 根据userId分页获取视频列表，支持按关键词搜索
     */
    public List<Video> getVideosByUserId(String userId, int skip, int limit, String keyword) {
        Criteria criteria = Criteria.where("uploaderId").is(userId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            criteria.orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("tags").regex(regex, "i")
            );
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Video.class);
    }

    /**
     * 统计用户视频总数，支持按关键词筛选
     */
    public long countVideosByUserId(String userId, String keyword) {
        Criteria criteria = Criteria.where("uploaderId").is(userId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            criteria.orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("tags").regex(regex, "i")
            );
        }
        return mongoTemplate.count(Query.query(criteria), Video.class);
    }

    /**
     * 分页获取公开视频列表，支持按关键词搜索
     */
    public List<Video> getPublicVideoList(int skip, int limit, String keyword) {
        Criteria criteria = Criteria.where("visibility").is("PUBLIC");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("title").regex(regex, "i"),
                            Criteria.where("description").regex(regex, "i"),
                            Criteria.where("tags").regex(regex, "i")
                    )
            );
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Video.class);
    }

    /**
     * 统计公开视频总数，支持按关键词筛选
     */
    public long countPublicVideos(String keyword) {
        Criteria criteria = Criteria.where("visibility").is("PUBLIC");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("title").regex(regex, "i"),
                            Criteria.where("description").regex(regex, "i"),
                            Criteria.where("tags").regex(regex, "i")
                    )
            );
        }
        return mongoTemplate.count(Query.query(criteria), Video.class);
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
     * 搜索公开视频，支持关键词和分类筛选
     */
    public List<Video> searchPublicVideos(String keyword, String category, int skip, int limit) {
        Criteria criteria = Criteria.where("visibility").is("PUBLIC");
        List<Criteria> andCriteria = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("tags").regex(regex, "i")
            ));
        }
        if (category != null && !category.trim().isEmpty()) {
            andCriteria.add(Criteria.where("category").is(category));
        }
        if (!andCriteria.isEmpty()) {
            criteria.andOperator(andCriteria.toArray(new Criteria[0]));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Video.class);
    }

    /**
     * 统计搜索匹配的公开视频总数
     */
    public long countSearchPublicVideos(String keyword, String category) {
        Criteria criteria = Criteria.where("visibility").is("PUBLIC");
        List<Criteria> andCriteria = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String regex = Pattern.quote(keyword.trim());
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex, "i"),
                    Criteria.where("description").regex(regex, "i"),
                    Criteria.where("tags").regex(regex, "i")
            ));
        }
        if (category != null && !category.trim().isEmpty()) {
            andCriteria.add(Criteria.where("category").is(category));
        }
        if (!andCriteria.isEmpty()) {
            criteria.andOperator(andCriteria.toArray(new Criteria[0]));
        }
        return mongoTemplate.count(Query.query(criteria), Video.class);
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
