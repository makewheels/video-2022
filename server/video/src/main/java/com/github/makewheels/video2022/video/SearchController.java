package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.video.bean.vo.SearchResultVO;
import com.github.makewheels.video2022.video.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("search")
@Slf4j
public class SearchController {
    @Resource
    private SearchService searchService;

    /**
     * 搜索公开视频
     */
    @GetMapping
    public Result<SearchResultVO> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return searchService.search(q, category, page, pageSize);
    }
}
