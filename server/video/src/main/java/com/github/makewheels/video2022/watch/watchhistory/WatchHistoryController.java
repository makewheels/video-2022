package com.github.makewheels.video2022.watch.watchhistory;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("watchHistory")
public class WatchHistoryController {
    @Resource
    private WatchHistoryService watchHistoryService;

    /**
     * 获取我的观看历史
     */
    @GetMapping("getMyHistory")
    public Result<Map<String, Object>> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = UserHolder.getUserId();
        return watchHistoryService.getMyHistory(userId, page, pageSize);
    }

    /**
     * 清除观看历史
     */
    @DeleteMapping("clear")
    public Result<Void> clear() {
        String userId = UserHolder.getUserId();
        return watchHistoryService.clearHistory(userId);
    }
}
