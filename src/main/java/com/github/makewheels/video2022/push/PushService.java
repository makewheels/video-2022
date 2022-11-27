package com.github.makewheels.video2022.push;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class PushService {
    public JSONObject sendEmail(String fromAlias, String subject, String htmlBody) {
        //组装发送邮件参数
        JSONObject body = new JSONObject();
        body.put("toAddress", "finalbird@foxmail.com");
        body.put("fromAlias", fromAlias);
        body.put("subject", subject);
        body.put("htmlBody", htmlBody);
        //调用推送中心
        String response = HttpUtil.post("http://82.157.172.71:5025/push/sendEmail", body.toJSONString());
        return JSONObject.parseObject(response);
    }
}
