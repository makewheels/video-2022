package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.etc.context.Context;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("watchController")
public class WatchController {
    @Resource
    private WatchService watchService;

    /**
     * 增加观看记录
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(@RequestParam String videoStatus) {
        Context context = RequestUtil.toDTO(Context.class);
        return watchService.addWatchLog(context, videoStatus);
    }

    /**
     * 获取播放信息
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(@RequestParam String watchId) {
        Context context = RequestUtil.toDTO(Context.class);
        return watchService.getWatchInfo(context, watchId);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(@RequestParam String transcodeId, @RequestParam String resolution) {
        Context context = RequestUtil.toDTO(Context.class);
        return watchService.getM3u8Content(context, transcodeId, resolution);
    }

    /**
     * 获取自适应m3u8列表
     */
    @GetMapping("getMultivariantPlaylist.m3u8")
    public String getMultivariantPlaylist() {
        Context context = RequestUtil.toDTO(Context.class);
        return watchService.getMultivariantPlaylist(context);
    }
}
