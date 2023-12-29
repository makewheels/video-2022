package com.github.makewheels.video2022.watch.progress;

import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("progress")
public class ProgressController {
    @Resource
    private ProgressService progressService;

    /**
     * 获取视频播放进度
     */
    @GetMapping("getProgress")
    public Result<Progress> getProgress() {
        Context context = RequestUtil.getContext();
        Progress progress = progressService.getProgress(
                context.getVideoId(), UserHolder.getUserId(), context.getClientId());
        return Result.ok(progress);
    }
}
