package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.video.constants.VideoCategory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("category")
public class CategoryController {

    @GetMapping("list")
    public Result<List<String>> list() {
        return Result.ok(VideoCategory.ALL);
    }
}
