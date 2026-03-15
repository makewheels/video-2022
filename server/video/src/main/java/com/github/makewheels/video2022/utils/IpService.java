package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class IpService {
    @Value("${aliyun.apigateway.ip.appcode}")
    private String appCode;

    @Resource
    private MongoTemplate mongoTemplate;

    private JSONObject getIpInfoFromApi(String ip) {
        String json = HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + ip)
                .header("Authorization", "APPCODE " + appCode).execute().body();
        log.info("调用阿里云云市场接口获取ip信息，传入 {}，返回 {}", ip, json);
        return JSON.parseObject(json);
    }

    private void handleApiResponse(String ip, JSONObject result) {
        result.put("ip", ip);
        result.putAll(result.getJSONObject("result"));
        result.remove("result");
        log.debug("处理ip响应结果handleApiResponse " + result.toJSONString());
    }

    public JSONObject getIpInfo(String ip) {
        String cacheKey = ip.replace(":", "_");
        // Check MongoDB cache
        IpCache cached = mongoTemplate.findOne(
                new Query(Criteria.where("ip").is(cacheKey)), IpCache.class);
        if (cached != null) {
            return JSON.parseObject(cached.getLocationJson());
        }

        // Cache miss — call API
        JSONObject result = getIpInfoFromApi(ip);
        handleApiResponse(ip, result);

        // Save to MongoDB cache
        IpCache ipCache = new IpCache();
        ipCache.setIp(cacheKey);
        ipCache.setLocationJson(result.toJSONString());
        ipCache.setCreatedAt(new Date());
        mongoTemplate.save(ipCache);

        return result;
    }
}
