package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.utils;

import com.alibaba.fastjson.JSON;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.sms.SmsClient;
import com.baidubce.services.sms.SmsClientConfiguration;
import com.baidubce.services.sms.model.SendMessageV3Request;
import com.baidubce.services.sms.model.SendMessageV3Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class BaiduSmsService {
    @Value("${baidu.sms.accessKeyId}")
    private String accessKeyId;
    @Value("${baidu.sms.secretKey}")
    private String secretKey;

    private SmsClient client;

    private SmsClient getClient() {
        if (client == null) {
            SmsClientConfiguration config = new SmsClientConfiguration();
            config.setCredentials(new DefaultBceCredentials(accessKeyId, secretKey));
            config.setEndpoint("https://smsv3.bj.baidubce.com");
            client = new SmsClient(config);
        }
        return client;
    }

    /**
     * 发送验证码短信
     */
    public SendMessageV3Response sendVerificationCode(String phone, Map<String, String> contentVar) {
        SendMessageV3Request request = new SendMessageV3Request();
        request.setMobile(phone);
        request.setSignatureId("sms-sign-QeEHQe10478");
        request.setTemplate("sms-tmpl-HNbGLw26882");
        request.setContentVar(contentVar);
        log.info("百度发短信 request：{}", JSON.toJSONString(request));
        SendMessageV3Response response = getClient().sendMessage(request);
        log.info("百度发短信 response：{}", JSON.toJSONString(response));
        return response;
    }

}
