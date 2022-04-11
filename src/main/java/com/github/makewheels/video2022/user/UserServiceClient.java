package com.github.makewheels.video2022.user;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
public class UserServiceClient {
    private String getBaseUrl() {
        return "http://localhost:5029/user-micro-service-2022/user/";
    }

    public User getUserByToken(String token) {
        if (StringUtils.isEmpty(token)) return null;
        String json = HttpUtil.get(getBaseUrl() + "getUserByToken?token=" + token);
        JSONObject data = JSONObject.parseObject(json).getJSONObject("data");
        if (data == null) return null;
        return JSON.parseObject(data.toJSONString(), User.class);
    }

    public User getUserByRequest(HttpServletRequest request) {
        //为了更简单的，兼容YouTube搬运海外服务器，获取上传凭证时的，用户校验，
        //获取token方式有两种，header和url参数
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            token = request.getParameterMap().get("token")[0];
        }
        return getUserByToken(token);
    }
}
