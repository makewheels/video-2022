package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
import com.github.makewheels.video2022.transcode.local.LocalTranscodeService;
import com.github.makewheels.video2022.transcode.local.dto.CreateLocalTranscodeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("transcode")
@Slf4j
public class TranscodeController {
    @Resource
    private TranscodeFactory transcodeFactory;
    @Resource
    private LocalTranscodeService localTranscodeService;

    @Value("${callback.secret}")
    private String callbackSecret;

    /**
     * 阿里云 云函数转码完成回调
     */
    @PostMapping("aliyunCloudFunctionTranscodeCallback")
    public ResponseEntity<Result<Void>> aliyunCloudFunctionTranscodeCallback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestBody JSONObject body) {
        if (callbackSecret == null || !callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("收到阿里云 云函数转码回调：" + body.toJSONString());
        String jobId = body.getString("jobId");
        TranscodeService transcodeService
                = transcodeFactory.getService(TranscodeProvider.ALIYUN_CLOUD_FUNCTION);
        transcodeService.callback(jobId);
        return ResponseEntity.ok(Result.ok());
    }

    /**
     * 本地转码：登记一档分辨率的转码任务，返回 OSS 上传目标与凭证。
     * 客户端凭返回的凭证把本机 FFmpeg 转好的 m3u8 + ts 直传到 outputDir，再调用 finish 登记。
     */
    @PostMapping("local/createTranscode")
    public Result<JSONObject> createLocalTranscode(@RequestBody CreateLocalTranscodeRequest request) {
        return Result.ok(localTranscodeService.createTranscode(request));
    }

    /**
     * 本地转码：客户端转码产物上传完成后回调，复用现有登记逻辑扫描 OSS 登记 ts、置视频就绪。
     */
    @PostMapping("local/finishTranscode")
    public Result<Void> finishLocalTranscode(@RequestParam String transcodeId) {
        localTranscodeService.finishTranscode(transcodeId);
        return Result.ok();
    }
}
