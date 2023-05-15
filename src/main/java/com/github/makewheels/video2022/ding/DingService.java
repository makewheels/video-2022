package com.github.makewheels.video2022.ding;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSON;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.etc.api.ApiType;
import com.github.makewheels.video2022.etc.api.DingApi;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;
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
    @Resource
    private RobotFactory robotFactory;
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 签名
     */
    private String generateUrl(String accessToken, String secret) {
        String baseUrl = "https://oapi.dingtalk.com/robot/send?";
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.encode(signData), "UTF-8");
            return baseUrl + "access_token=" + accessToken + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 按照机器人类型生成url
     */
    private String generateUrl(String robotType) {
        RobotConfig robotConfig = robotFactory.getRobotByType(robotType);
        return generateUrl(robotConfig.getAccessToken(), robotConfig.getSecret());
    }

    /**
     * 创建一个初始化对象
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
     * {"body":"{\"errcode\":0,\"errmsg\":\"ok\"}","errcode":0,"errmsg":"ok","errorCode":"0","msg":"ok",
     * "params":{"markdown":"{\"text\":\"27f13623-9719-4a19-a914-597d315b931d\\n\\nfefg\",
     * \"title\":\"test-title\"}","msgtype":"markdown"},"subCode":"","subMsg":"","success":true}
     */
    public OapiRobotSendResponse sendMarkdown(String robotType, String title, String markdownText) {
        DingApi dingApi = createApiLog(title, markdownText);
        dingApi.setMessageType("markdown");
        log.info("钉钉发送消息：title: {}", title);
        log.info("钉钉发送消息：markdownText: {}", markdownText);

        String url = generateUrl(robotType);
        DingTalkClient client = new DefaultDingTalkClient(url);
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
