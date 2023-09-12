package com.github.makewheels.video2022.archive;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.system.environment.EnvironmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class ShortUrlService {
    @Resource
    private EnvironmentService environmentService;

    public String getShortUrl(String fullUrl) {
        JSONObject body = new JSONObject();
        body.put("fullUrl", fullUrl);
        body.put("sign", "DuouXm25hwFWVbUmyw3a");
        String response = HttpUtil.post(environmentService.getShortUrlService(), body.toJSONString());
        log.info("getShortUrl: body = {}, response = {}",
                JSON.toJSONString(body), JSON.toJSONString(response));
        return response;
    }
}
