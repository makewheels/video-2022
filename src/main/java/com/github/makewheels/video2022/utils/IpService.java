package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class IpService {
    @Value("${aliyun.apigateway.ip.appcode}")
    private String appCode;

    @Resource
    private RedisService redisService;

    private JSONObject getIpInfoFromApi(String ip) {
        String json = HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + ip)
                .header("Authorization", "APPCODE " + appCode).execute().body();
        return JSON.parseObject(json);
    }

    public JSONObject getIpWithRedis(String ip) {
        String ipForRedisKey = ip.replace(":", "_");
        //先查Redis，如果有直接返回
        JSONObject jsonObject = redisService.getForJSONObject(RedisKey.ip(ipForRedisKey));
        if (jsonObject != null) {
            jsonObject.put("ip", ip);
            return jsonObject;
        }

        //如果Redis没有，调阿里云接口
        JSONObject ipInfoFromApi = getIpInfoFromApi(ip);
        ipInfoFromApi.put("ip", ip);

        redisService.set(RedisKey.ip(ipForRedisKey), ipInfoFromApi.toJSONString(), RedisTime.SIX_HOURS);
        return jsonObject;
    }

    public JSONObject getIpResultWithRedis(String ip) {
        try {
            JSONObject ipResponse = getIpWithRedis(ip);
            ipResponse.getJSONObject("result");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
