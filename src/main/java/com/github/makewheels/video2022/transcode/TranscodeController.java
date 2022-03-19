package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("transcode")
@Slf4j
public class TranscodeController {
    @RequestMapping("callback")
    public void callback(@RequestBody JSONObject jsonObject) {
        System.out.println(jsonObject);

    }
}
