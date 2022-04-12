package com.github.makewheels.video2022.cdn;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.response.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class CdnController {
    @Resource
    private CdnService cdnService;

    /**
     * 在软路由预热完成时
     */
    @PostMapping("onSoftRoutePrefetchFinish")
    public Result<Void> onSoftRoutePrefetchFinish(@RequestBody JSONObject body) {
        return cdnService.onSoftRoutePrefetchFinish(body);
    }
}
