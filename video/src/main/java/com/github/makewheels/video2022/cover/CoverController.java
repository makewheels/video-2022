package com.github.makewheels.video2022.cover;

import com.github.makewheels.video2022.etc.system.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("cover")
@Slf4j
public class CoverController {
    @Resource
    private CoverCallbackService coverCallbackService;

    /**
     * youtube封面完成回调
     */
    @GetMapping("youtubeUploadFinishCallback")
    public Result<Void> youtubeUploadFinishCallback(@RequestParam String coverId) {
        log.debug("收到youtube封面完成回调：coverId = {}", coverId);
        return coverCallbackService.youtubeUploadFinishCallback(coverId);
    }

}
