package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.etc.context.Context;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("watchController")
public class WatchController {
    @Resource
    private WatchService watchService;

    /**
     * 增加观看记录
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(HttpServletRequest request, @RequestParam String videoStatus) {
        Context context = RequestUtil.toDTO(request, Context.class);
        return watchService.addWatchLog(request, context, videoStatus);
    }

    /**
     * 获取播放信息
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(
            HttpServletRequest request, @RequestParam String watchId) {
        Context context = RequestUtil.toDTO(request, Context.class);
        return watchService.getWatchInfo(context, watchId);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(
            HttpServletRequest request, @RequestParam String transcodeId,
            @RequestParam String resolution) {
        Context context = RequestUtil.toDTO(request, Context.class);
        return watchService.getM3u8Content(context, transcodeId, resolution);
    }

    /**
     * 获取自适应m3u8列表
     */
    @GetMapping("getMultivariantPlaylist.m3u8")
    public String getMultivariantPlaylist(HttpServletRequest request) {
        Context context = RequestUtil.toDTO(request, Context.class);
        return watchService.getMultivariantPlaylist(context);
    }
}
