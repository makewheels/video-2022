package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
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
    @Resource
    private UserService userService;

    /**
     * 增加观看记录
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(
            HttpServletRequest request, @RequestParam String videoId, @RequestParam String clientId,
            @RequestParam String sessionId, @RequestParam String videoStatus) {
        return watchService.addWatchLog(request, clientId, sessionId, videoId, videoStatus);
    }

    /**
     * 获取播放信息
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(
            HttpServletRequest request, @RequestParam String watchId,
            @RequestParam String clientId, @RequestParam String sessionId) {
        User user = userService.getUserByRequest(request);
        return watchService.getWatchInfo(user, watchId, clientId, sessionId);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(
            HttpServletRequest request, @RequestParam String videoId,
            @RequestParam String clientId, @RequestParam String sessionId,
            @RequestParam String transcodeId, @RequestParam String resolution) {
        return watchService.getM3u8Content(videoId, clientId, sessionId, transcodeId, resolution);
    }

    /**
     * 获取自适应m3u8列表
     */
    @GetMapping("getMultivariantPlaylist")
    public String getMultivariantPlaylist(
            HttpServletRequest request, @RequestParam String videoId,
            @RequestParam String clientId, @RequestParam String sessionId) {
        return watchService.getMultivariantPlaylist(videoId, clientId, sessionId);
    }
}
