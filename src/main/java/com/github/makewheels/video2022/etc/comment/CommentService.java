package com.github.makewheels.video2022.etc.comment;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class CommentService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private CommentRepository commentRepository;

    /**
     * 新增评论
     *
     * @param body
     * @return
     */
    public Result<Void> add(JSONObject body) {
        Comment comment = new Comment();
        mongoTemplate.save(comment);
        return Result.ok();
    }

    /**
     * 根据videoId分页获取评论列表
     *
     * @param videoId
     * @param skip
     * @param limit
     * @return
     */
    public Result<List<Comment>> getByVideoId(String videoId, int skip, int limit) {
        List<Comment> comments = commentRepository.getCommentsByVideoId(videoId, skip, limit);
        comments.forEach(comment -> comment.setSessionId(null));
        return Result.ok(comments);
    }
}
