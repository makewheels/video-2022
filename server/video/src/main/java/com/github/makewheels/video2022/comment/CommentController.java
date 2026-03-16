package com.github.makewheels.video2022.comment;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("comment")
public class CommentController {
    @Resource
    private CommentService commentService;
    @Resource
    private CheckService checkService;

    @PostMapping("add")
    public Result<Comment> add(@RequestBody JSONObject body) {
        String videoId = body.getString("videoId");
        String content = body.getString("content");
        String parentId = body.getString("parentId");
        checkService.checkVideoExist(videoId);
        if (content == null || content.trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }
        if (content.length() > 2000) {
            return Result.error("评论内容不能超过2000字");
        }
        return commentService.addComment(videoId, content.trim(), parentId);
    }

    @GetMapping("getByVideoId")
    public Result<CommentPageVO> getByVideoId(
            @RequestParam String videoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createTime") String sortBy) {
        checkService.checkVideoExist(videoId);
        return commentService.getByVideoIdPaginated(videoId, page, Math.min(pageSize, 50), sortBy);
    }

    @GetMapping("getReplies")
    public Result<List<Comment>> getReplies(
            @RequestParam String parentId,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return commentService.getReplies(parentId, skip, Math.min(limit, 50));
    }

    @GetMapping("delete")
    public Result<Void> delete(@RequestParam String commentId) {
        return commentService.deleteComment(commentId);
    }

    @GetMapping("like")
    public Result<Void> like(@RequestParam String commentId) {
        return commentService.likeComment(commentId);
    }

    @GetMapping("getCount")
    public Result<JSONObject> getCount(@RequestParam String videoId) {
        checkService.checkVideoExist(videoId);
        return commentService.getCount(videoId);
    }
}
