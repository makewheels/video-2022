package com.github.makewheels.video2022.utils;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.taobao.api.ApiException;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 钉钉机器人工具类
 */
public class DingUtil {
    private final static String webhookUrl = "https://oapi.dingtalk.com/robot/send?" +
            "access_token=960a84c7fac8bb09ac013bd0ed23b7085282d5dc59aeb3c08562c1fb3e961699";
    private final static String secret
            = "SEC924dc9d2ba24b5f5354f674adc81d33626ed997e1823de89f04338e581cebb1c";

    private static String getUrl() {
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
     * 发送 markdown
     */
    public static OapiRobotSendResponse sendMarkdown(String title, String markdowntext) {
        DingTalkClient client = new DefaultDingTalkClient(getUrl());
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("markdown");
        OapiRobotSendRequest.Markdown markdown = new OapiRobotSendRequest.Markdown();
        markdown.setTitle(title);
        markdown.setText(markdowntext);
        request.setMarkdown(markdown);
        try {
            return client.execute(request);
        } catch (ApiException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        String text = UUID.randomUUID() + "\n\n" + "fefg";
        System.out.println(text);
        OapiRobotSendResponse response = sendMarkdown("test-title", text);
        if (response != null) {
            System.out.println(response.isSuccess());
            System.out.println(response.getErrmsg());
        }
    }
}
