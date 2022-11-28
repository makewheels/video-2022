package com.github.makewheels.video2022.etc.ip;

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

    public JSONObject getIpWithRedis(String ip) {
        //先查Redis，如果有直接返回
        JSONObject jsonObject = redisService.getForJSONObject(RedisKey.ip(ip));
        if (jsonObject != null) {
            return jsonObject;
        }

        //如果Redis没有，调阿里云接口
        String json = HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + ip)
                .header("Authorization", "APPCODE " + appCode).execute().body();
        jsonObject = JSON.parseObject(json);
        redisService.set(RedisKey.ip(ip), json, RedisTime.THREE_HOURS);
        return jsonObject;
    }

}