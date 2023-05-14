package com.github.makewheels.video2022.utils;

import com.alibaba.fastjson.JSON;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.etc.api.ApiType;
import com.github.makewheels.video2022.etc.api.DingApi;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 钉钉机器人推送
 */
@Service
@Slf4j
public class DingService {
    private String webhookUrl = "https://oapi.dingtalk.com/robot/send?" +
            "access_token=960a84c7fac8bb09ac013bd0ed23b7085282d5dc59aeb3c08562c1fb3e961699";
    private String secret
            = "SEC924dc9d2ba24b5f5354f674adc81d33626ed997e1823de89f04338e581cebb1c";
    @Resource
    private MongoTemplate mongoTemplate;

    private String getUrl() {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(new String(new Base64().encode(signData)), "UTF-8");
            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 创建一个初始化对象
     *
     * @param title
     * @param text
     * @return
     */
    private DingApi createApiLog(String title, String text) {
        DingApi dingApi = new DingApi();
        dingApi.setType(ApiType.DING);
        dingApi.setStartTime(new Date());

        dingApi.setTitle(title);
        dingApi.setText(text);
        return dingApi;
    }

    /**
     * 发送 markdown
     * <p>
     * 示例返回：
     * {"body":"{\"errcode\":0,\"errmsg\":\"ok\"}","errcode":0,"errmsg":"ok","errorCode":"0","msg":"ok","params":{"markdown":"{\"text\":\"27f13623-9719-4a19-a914-597d315b931d\\n\\nfefg\",\"title\":\"test-title\"}","msgtype":"markdown"},"subCode":"","subMsg":"","success":true}
     */
    public OapiRobotSendResponse sendMarkdown(String title, String markdownText) {
        DingApi dingApi = createApiLog(title, markdownText);
        dingApi.setMessageType("markdown");
        log.info("钉钉发送消息：title: {}", title);
        log.info("钉钉发送消息：markdownText: {}", markdownText);
        DingTalkClient client = new DefaultDingTalkClient(getUrl());
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("markdown");

        dingApi.setRequest(JSON.parseObject(JSON.toJSONString(request)));
        log.info("钉钉发送消息：request: {}", JSON.toJSONString(request));
        OapiRobotSendRequest.Markdown markdown = new OapiRobotSendRequest.Markdown();
        markdown.setTitle(title);
        markdown.setText(markdownText);
        request.setMarkdown(markdown);
        OapiRobotSendResponse response = null;
        try {
            response = client.execute(request);
        } catch (ApiException e) {
            e.printStackTrace();
        }

        dingApi.setEndTime(new Date());
        dingApi.setCost(dingApi.getEndTime().getTime() - dingApi.getStartTime().getTime());
        dingApi.setCode(response.getErrorCode());
        dingApi.setMessage(response.getMessage());
        dingApi.setIsSuccess(response.isSuccess());
        dingApi.setResponse(JSON.parseObject(JSON.toJSONString(response)));

        mongoTemplate.save(dingApi);
        log.info("钉钉发送消息：response: {}", JSON.toJSONString(response));
        return response;
    }

}
