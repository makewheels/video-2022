package com.github.makewheels.video2022.wechat;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

@RestController
@RequestMapping("miniProgram")
public class MiniProgramController {
    @Value("${mini-program.env}")
    private String miniProgramEnv;

    private JSONObject getAccessToken() {
        String json = HttpUtil.get("https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential&appid=wx2b94f07ee281d8ce"
                + "&secret=cbb6376401f724ab412322367c3fdd87");
        return JSON.parseObject(json);
    }

    private InputStream getImageInputStream(String accessToken, String videoId) {
        JSONObject param = new JSONObject();
        param.put("videoId", videoId);
        param.put("page", "pages/share/share");
        if (miniProgramEnv.equals("dev")) {
            param.put("check_path", false);
            param.put("env_version", "trial");
        }
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;
        HttpResponse response = HttpUtil.createPost(url).body(param.toJSONString()).execute();
        return response.bodyStream();
    }

}
