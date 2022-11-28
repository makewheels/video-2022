package com.github.makewheels.video2022.ip;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.user.User;
import org.springframework.stereotype.Service;

@Service
public class IpService {
    public JSONObject getIpWithRedis(String ip) {
        //判断Redis有没有

        //如果没有，调阿里云接口
        HttpRequest httpRequest = HttpUtil.createGet("https://ips.market.alicloudapi.com/iplocaltion?ip=" + ip);


        String json = httpRequest.execute().body();
        return JSON.parseObject(json);
    }
}
