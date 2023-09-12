package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.etc.system.context.Context;
import com.github.makewheels.video2022.etc.system.context.RequestUtil;
import com.github.makewheels.video2022.etc.system.response.Result;
import com.github.makewheels.video2022.watch.watchinfo.WatchInfoVO;
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
    @Resource
    private CheckService checkService;

    /**
     * 增加观看记录
     */
    @GetMapping("addWatchLog")
    public Result<Void> addWatchLog(@RequestParam String videoStatus) {
        Context context = RequestUtil.getContext();
        String videoId = context.getVideoId();
        checkService.checkVideoExist(videoId);
        return watchService.addWatchLog(RequestUtil.getContext(), videoStatus);
    }

    /**
     * 获取播放信息
     */
    @GetMapping("getWatchInfo")
    public Result<WatchInfoVO> getWatchInfo(@RequestParam String watchId) {
        checkService.checkWatchIdExist(watchId);
        return watchService.getWatchInfo(RequestUtil.getContext(), watchId);
    }

    /**
     * 获取m3u8内容，里面是ts列表链接
     * 需要以.m3u8结尾，播放器才能识别
     */
    @GetMapping("getM3u8Content.m3u8")
    public String getM3u8Content(@RequestParam String transcodeId, @RequestParam String resolution) {
        return watchService.getM3u8Content(RequestUtil.getContext(), transcodeId, resolution);
    }

    /**
     * 获取自适应m3u8列表
     * 需要以.m3u8结尾，播放器才能识别
     */
    @GetMapping("getMultivariantPlaylist.m3u8")
    public String getMultivariantPlaylist() {
        return watchService.getMultivariantPlaylist(RequestUtil.getContext());
    }
}
