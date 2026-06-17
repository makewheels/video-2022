package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.local.LocalCoverService;
import com.github.makewheels.video2022.cover.local.dto.CreateLocalCoverRequest;
import com.github.makewheels.video2022.system.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("cover")
@Slf4j
public class CoverController {
    @Resource
    private CoverCallbackService coverCallbackService;
    @Resource
    private LocalCoverService localCoverService;

    @Value("${callback.secret}")
    private String callbackSecret;

    /**
     * youtube封面完成回调
     */
    @GetMapping("youtubeUploadFinishCallback")
    public ResponseEntity<Result<Void>> youtubeUploadFinishCallback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestParam String coverId) {
        if (callbackSecret == null || !callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.debug("收到youtube封面完成回调：coverId = {}", coverId);
        return ResponseEntity.ok(coverCallbackService.youtubeUploadFinishCallback(coverId));
    }

    /**
     * 本地封面：登记封面记录，返回 OSS 上传目标与凭证。
     * 客户端凭返回的凭证把本机 FFmpeg 截好的封面直传到 coverKey，再调用 finish 置就绪。
     */
    @PostMapping("local/createCover")
    public Result<JSONObject> createLocalCover(@RequestBody CreateLocalCoverRequest request) {
        return Result.ok(localCoverService.createCover(request));
    }

    /**
     * 本地封面：封面上传完成后回调，置封面就绪并挂到视频。
     */
    @PostMapping("local/finishCover")
    public Result<Void> finishLocalCover(@RequestParam String coverId) {
        localCoverService.finishCover(coverId);
        return Result.ok();
    }

}
