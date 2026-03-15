package com.github.makewheels.video2022.etc.app;

import com.github.makewheels.video2022.system.response.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("app")
public class AppController {
    @Resource
    private AppService appService;

    @GetMapping("checkUpdate")
    public Result<CheckUpdateResponse> checkUpdate(
            @RequestParam String platform,
            @RequestParam(required = false) Integer versionCode) {
        return appService.checkUpdate(platform, versionCode);
    }

    @PostMapping("publishVersion")
    public Result<AppVersion> publishVersion(@RequestBody PublishVersionRequest request) {
        return appService.publishVersion(request);
    }
}
