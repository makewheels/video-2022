package com.github.makewheels.video2022.etc.app;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
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
        response.put("versionCode", 1);
        response.put("versionName", "1.0.0");
        response.put("versionInfo", "最新版本信息：alpha内测，2022年4月25日20:41:46");
        response.put("isForceUpdate", false);
        response.put("downloadUrl", "https://baidu.com");
        response.put("compareVersion", false);
        return Result.ok(response);
    }
}
