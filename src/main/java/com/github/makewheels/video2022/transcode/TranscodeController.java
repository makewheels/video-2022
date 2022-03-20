package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("transcode")
@Slf4j
public class TranscodeController {
    @Resource
    private TranscodeService transcodeService;

    /**
     * 当转码状态发生变化时，处理回调
     *
     * @param jsonObject
     */
    @RequestMapping("callback")
    public Result<Void> callback(@RequestBody JSONObject jsonObject) {
        log.info("收到视频处理回调：");
        log.info(jsonObject.toJSONString());
        return transcodeService.callback(jsonObject);
    }
}
