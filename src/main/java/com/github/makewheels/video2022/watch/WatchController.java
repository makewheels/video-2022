package com.github.makewheels.video2022.watch;

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
        return watchService.addWatchLog(RequestUtil.getContext(), videoStatus);
    }

    /**
     * 获取播放信息
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfo> getWatchInfo(@RequestParam String watchId) {
        return watchService.getWatchInfo(RequestUtil.getContext(), watchId);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(@RequestParam String transcodeId, @RequestParam String resolution) {
        return watchService.getM3u8Content(RequestUtil.getContext(), transcodeId, resolution);
    }

    /**
     * 获取自适应m3u8列表
     */
    @GetMapping("getMultivariantPlaylist.m3u8")
    public String getMultivariantPlaylist() {
        return watchService.getMultivariantPlaylist(RequestUtil.getContext());
    }
}
