package com.github.makewheels.video2022;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.usermicroservice2022.User;
import org.springframework.stereotype.Service;

@Service
public class UserClient {
    private String getBaseUrl() {
        return "http://localhost:5021/user/";
    }

    public User getUserByToken(String token) {
        String json = HttpUtil.get(getBaseUrl() + "getUserByToken?token=" + token);
        return JSON.parseObject(json, User.class);
    }
}
