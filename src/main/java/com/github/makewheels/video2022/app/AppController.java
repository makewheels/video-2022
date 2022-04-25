package com.github.makewheels.video2022.app;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("app")
public class AppController {
    @GetMapping("checkUpdate")
    public Result<JSONObject> checkUpdate(@RequestParam String platform) {
        JSONObject response = new JSONObject();
        response.put("latestVersionCode", 1);
        response.put("latestVersionName", "1.0.0");
        response.put("latestVersionInfo", "最新版本信息：alpha内测，2022年4月25日20:41:46");
        response.put("isForceUpdate", false);
        response.put("downloadUrl", "http://baidu.com");
        return Result.ok(response);
    }
}
