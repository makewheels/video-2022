package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("transcode")
@Slf4j
public class TranscodeController {
    @Resource
    private TranscodeCallbackService transcodeCallbackService;

    /**
     * 阿里云 云函数转码完成回调
     */
    @PostMapping("aliyunCloudFunctionTranscodeCallback")
    public Result<Void> aliyunCloudFunctionTranscodeCallback(@RequestBody JSONObject body) {
        log.info("收到阿里云 云函数转码回调：" + body.toJSONString());
        transcodeCallbackService.aliyunCloudFunctionTranscodeCallback(body);
        return Result.ok();
    }
}
