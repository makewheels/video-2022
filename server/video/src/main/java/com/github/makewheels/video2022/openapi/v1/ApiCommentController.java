package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.comment.Comment;
import com.github.makewheels.video2022.comment.CommentService;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.openapi.v1.dto.AddCommentApiRequest;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Comments", description = "评论管理")
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class ApiCommentController {
    @Resource
    private ApiAuthHelper apiAuthHelper;
    @Resource
    private CommentService commentService;
    @Resource
    private CheckService checkService;

    @Operation(summary = "获取视频评论列表")
    @GetMapping("/videos/{videoId}/comments")
    public Result<List<Comment>> getComments(
            @PathVariable String videoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkService.checkVideoExist(videoId);
        int skip = (page - 1) * size;
        int limit = Math.min(size, 50);
        return commentService.getByVideoId(videoId, skip, limit, "time");
    }

    @Operation(summary = "添加评论")
    @PostMapping("/videos/{videoId}/comments")
    public Result<Comment> addComment(
            @PathVariable String videoId,
            @RequestBody AddCommentApiRequest request) {
        try {
            apiAuthHelper.setupUserContext();
            checkService.checkVideoExist(videoId);
            String content = request.getContent();
            if (content == null || content.trim().isEmpty()) {
                return Result.error("评论内容不能为空");
            }
            if (content.length() > 2000) {
                return Result.error("评论内容不能超过2000字");
            }
            return commentService.addComment(videoId, content.trim(), request.getParentId());
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable String commentId) {
        try {
            apiAuthHelper.setupUserContext();
            return commentService.deleteComment(commentId);
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }
}
