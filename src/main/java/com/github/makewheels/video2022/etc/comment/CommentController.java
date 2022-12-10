package com.github.makewheels.video2022.etc.comment;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("comment")
public class CommentController {
    @Resource
    private CommentService commentService;

    @PostMapping("add")
    public Result<Void> add(@RequestBody JSONObject body) {
        return commentService.add(body);
    }

    @GetMapping("getByVideoId")
    public Result<List<Comment>> getByVideoId(
            @RequestParam String videoId, @RequestParam int skip, @RequestParam int limit) {
        return commentService.getByVideoId(videoId, skip, limit);
    }
}
