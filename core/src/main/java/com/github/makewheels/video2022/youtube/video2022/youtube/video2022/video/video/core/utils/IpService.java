package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class IpService {
    @Value("${aliyun.apigateway.ip.appcode}")
    private String appCode;

    @Resource
    private RedisService redisService;

    /**
     * 从云市场获取ip信息
     * 示例返回值：
     * {"code":100,"message":"success","ip":"45.78.33.111",
     * "result":{
     * "en_short":"CN",
     * "en_name":"China",
     * "nation":"中国",
     * "province":"香港特别行政区",
     * "city":"",
     * "district":"",
     * "adcode":"810000",
     * "lat":22.27628,
     * "lng":114.16383}
     * }
     */
    private JSONObject getIpInfoFromApi(String ip) {
        String json = HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + ip)
                .header("Authorization", "APPCODE " + appCode).execute().body();
        log.info("调用阿里云云市场接口获取ip信息，传入 {}，返回 {}", ip, json);
        return JSON.parseObject(json);
    }

    /**
     * 处理响应结果
     */
    private void handleApiResponse(String ip, JSONObject result) {
        result.put("ip", ip);

        //把响应里的result提到外层
        result.putAll(result.getJSONObject("result"));
        result.remove("result");
        log.debug("处理ip响应结果handleApiResponse " + result.toJSONString());
    }

    public JSONObject getIpWithRedis(String ip) {
        String ipRedisKey = ip.replace(":", "_");
        //如果Redis有，直接返回
        JSONObject result = redisService.getForJSONObject(RedisKey.ip(ipRedisKey));
        if (result != null) {
            return result;
        }

        //如果Redis没有，调阿里云接口
        result = getIpInfoFromApi(ip);
        handleApiResponse(ip, result);

        //缓存到Redis
        redisService.set(RedisKey.ip(ipRedisKey), result.toJSONString(), RedisTime.SIX_HOURS);
        return result;
    }

}
