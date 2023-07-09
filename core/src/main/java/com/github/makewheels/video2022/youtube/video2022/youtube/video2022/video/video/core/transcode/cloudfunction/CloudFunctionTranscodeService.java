package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.transcode.cloudfunction;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CloudFunctionTranscodeService {
    @Value("${aliyun.oss.bucket}")
    private String bucket;
    @Value("${aliyun.oss.internal-endpoint}")
    private String endpoint;

    public String transcode(
            String inputKey, String outputDir, String videoId, String transcodeId, String jobId,
            String resolution, int width, int height, String videoCodec, String audioCodec,
            String quality, String callbackUrl) {

        JSONObject request = new JSONObject();
        request.put("bucket", bucket);
        request.put("endpoint", endpoint);
        request.put("inputKey", inputKey);
        request.put("outputDir", outputDir);
        request.put("videoId", videoId);
        request.put("transcodeId", transcodeId);
        request.put("jobId", jobId);
        request.put("resolution", resolution);
        request.put("width", width);
        request.put("height", height);
        request.put("videoCodec", videoCodec);
        request.put("audioCodec", audioCodec);
        request.put("quality", quality);
        request.put("callbackUrl", callbackUrl);
        log.info("发起阿里云 云函数转码 request = " + JSON.toJSONString(request));
        HttpRequest post = HttpUtil.createPost(
                "https://transcoe-master-video-transcode-pqrshwejna.cn-beijing.fcapp.run");
        post.body(request.toJSONString());
        post.header("X-Fc-Invocation-Type", "Async");
        HttpResponse response = post.execute();
        String requestId = response.header("X-Fc-Request-Id");
        log.info("调用阿里云 云函数发起转码：requestId = " + requestId);

        //异步调用，没有返回值
        return response.body();
    }

}
