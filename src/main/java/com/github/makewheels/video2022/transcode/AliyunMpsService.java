package com.github.makewheels.video2022.transcode;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.Client;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobRequest;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponse;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AliyunMpsService {
    @Value("${aliyun.mps.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.mps.secretKey}")
    private String accessKeySecret;

    private Client client;

    private Client getClient() {
        if (client != null) return client;
        Config config = new Config()
                .setAccessKeyId(Base64.decodeStr("TFRBSTV0UmdoeGdTNkJEMlYzN3BiWXUy"))
                .setAccessKeySecret(Base64.decodeStr("Tkk0ejVrS0J4UFlMMXFQNmFmRHJONjlmQUFDZE5p"));
//                .setAccessKeyId(Base64.decodeStr(accessKeyId))
//                .setAccessKeySecret(Base64.decodeStr(accessKeySecret));
        config.endpoint = "mts.cn-beijing.aliyuncs.com";
        config.protocol = "https";
        try {
            return new Client(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        SubmitMediaInfoJobRequest mediaInfoRequest = new SubmitMediaInfoJobRequest();
        mediaInfoRequest.setInput("{\"Bucket\":\"video-2022-dev\",\"Location\":" +
                "\"oss-cn-beijing\",\"Object\":\"videos%2F6231de9a5bffa00422da71ce%" +
                "2F6253dede8706fe314a47a54d%2Foriginal%2F6253dede8706fe314a47a54d.webm\"}");
        AliyunMpsService aliyunMpsService = new AliyunMpsService();
        Client client = aliyunMpsService.getClient();
        if (client == null) return;
        try {
            SubmitMediaInfoJobResponse response = client.submitMediaInfoJob(mediaInfoRequest);

            System.out.println(JSON.toJSONString(response.getBody()));

            String body = JSON.toJSONString(response.getBody());
            JSONObject jsonObject = JSON.parseObject(body);
            JSONObject properties = jsonObject.getJSONObject("mediaInfoJob").getJSONObject("properties");
            int width = Integer.parseInt(properties.getString("width"));
            int height = Integer.parseInt(properties.getString("height"));
            int duration = (int) (Double.parseDouble(properties.getString("duration")) * 1000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
