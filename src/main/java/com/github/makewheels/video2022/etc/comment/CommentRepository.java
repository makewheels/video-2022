package com.github.makewheels.video2022.etc.comment;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class CommentRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 根据videoId查找评论
     */
    public List<Comment> getCommentsByVideoId(String videoId, int skip, int limit) {
        Query query = Query.query(Criteria.where("videoId").is(videoId))
                //根据时间降序排列
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(query, Comment.class);
    }

}
