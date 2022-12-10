package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.etc.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("watchController")
public class WatchController {
    @Resource
    private WatchService watchService;

    /**
     * 增加观看记录
     *
     * @param request
     * @param videoId
     * @param clientId 前端先向用户微服务获取clientId，再调此接口
     * @return
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(
            HttpServletRequest request, @RequestParam String videoId, @RequestParam String clientId,
            @RequestParam String sessionId, @RequestParam String videoStatus) {
        return watchService.addWatchLog(request, clientId, sessionId, videoId, videoStatus);
    }
}
