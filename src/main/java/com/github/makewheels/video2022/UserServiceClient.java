package com.github.makewheels.video2022;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import org.springframework.stereotype.Service;

@Service
public class UserServiceClient {
    private String getBaseUrl() {
        return "http://localhost:5021/user-micro-service-2022/user/";
    }

    public User getUserByToken(String token) {
        String json = HttpUtil.get(getBaseUrl() + "getUserByToken?token=" + token);
        JSONObject data = JSONObject.parseObject(json).getJSONObject("data");
        if (data == null) {
            return null;
        }
        return JSON.parseObject(data.toJSONString(), User.class);
    }
}
