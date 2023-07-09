package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
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
    private TranscodeFactory transcodeFactory;

    /**
     * 阿里云 云函数转码完成回调
     */
    @PostMapping("aliyunCloudFunctionTranscodeCallback")
    public Result<Void> aliyunCloudFunctionTranscodeCallback(@RequestBody JSONObject body) {
        log.info("收到阿里云 云函数转码回调：" + body.toJSONString());
        String jobId = body.getString("jobId");
        TranscodeService transcodeService
                = transcodeFactory.getService(TranscodeProvider.ALIYUN_CLOUD_FUNCTION);
        transcodeService.callback(jobId);
        return Result.ok();
    }
}
