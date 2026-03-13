package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("transcode")
@Slf4j
public class TranscodeController {
    @Resource
    private TranscodeFactory transcodeFactory;

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
}
