package com.github.makewheels.video2022.etc.cdn;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.cdn20180510.Client;
import com.aliyun.cdn20180510.models.PushObjectCacheRequest;
import com.aliyun.cdn20180510.models.PushObjectCacheResponse;
import com.aliyun.teaopenapi.models.Config;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeProvider;
import com.github.makewheels.video2022.utils.M3u8Util;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CdnService {
    @Value("${aliyun.cdn.accessKeyId}")
    private String aliyunCdnAccessKeyId;
    @Value("${aliyun.cdn.secretKey}")
    private String aliyunCdnSecretKey;

    @Value("${cdn-prefetch-url}")
    private String cdnPrefetchUrl;
    @Value("${internal-base-url}")
    private String internalBaseUrl;

    /**
     * 软路由预热
     */
    private void softRoutePrefetch(Transcode transcode) {
        JSONObject request = new JSONObject();
        String missionId = IdUtil.getSnowflakeNextIdStr();
        request.put("missionId", missionId + "-" + transcode.getVideoId() + "-" + transcode.getId());
        //组装url list
        List<String> urlList = M3u8Util.getUrlListFromM3u8(transcode.getM3u8CdnUrl());
        request.put("urlList", urlList);
        request.put("callbackUrl", internalBaseUrl + "/cdn/onSoftRoutePrefetchFinish");
        log.info("通知软路由预热 resolution = " + transcode.getResolution() + ", size = " + urlList.size()
                + " " + request + request.toJSONString());
        String response = HttpUtil.post(cdnPrefetchUrl, request.toJSONString());
        log.info("请求预热，软路由回复：" + response);
    }

    /**
     * 在软路由cdn预热完成时
     *
     * @param body
     * @return
     */
    public Result<Void> onSoftRoutePrefetchFinish(JSONObject body) {
        log.info("收到软路由预热完成回调：" + body.toJSONString());
        return Result.ok();
    }

    /**
     * 通知阿里云cdn预热
     *
     * @return
     */
    private void aliyunCdnPrefetch(Transcode transcode) throws Exception {
        log.info("阿里云cdn预热 transcodeId = {}", transcode.getId());
        List<String> urlList = M3u8Util.getUrlListFromM3u8(transcode.getM3u8CdnUrl());
        log.info("预热urlList长度 = " + urlList.size());
        Config config = new Config()
                .setAccessKeyId(Base64.decodeStr(aliyunCdnAccessKeyId))
                .setAccessKeySecret(Base64.decodeStr(aliyunCdnSecretKey));
        config.setEndpoint("cdn.aliyuncs.com");
        Client client = new Client(config);

        //每次提交100个 https://help.aliyun.com/document_detail/27140.html#section-81a-rm1-sis
        Queue<String> queue = new LinkedList<>(urlList);
        while (!queue.isEmpty()) {
            int size = Math.min(queue.size(), 100);
            List<String> listForRequest = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                listForRequest.add(queue.poll());
            }
            PushObjectCacheRequest request = new PushObjectCacheRequest();
            request.setObjectPath(listForRequest.stream()
                    .map(e -> e + "\n").collect(Collectors.joining()));
            PushObjectCacheResponse response = client.pushObjectCache(request);
            log.info("发起阿里云预热cdn: size = {}, response = {}", size, JSON.toJSONString(response));
        }
    }


    /**
     * 预热cdn
     *
     * @param transcode
     */
    public void prefetchCdn(Transcode transcode) {
        String transcodeProvider = transcode.getProvider();
        //如果是阿里云mps转码，或者是阿里云 云函数转码，就发起阿里云cdn预热
        if (StringUtils.equalsAny(transcodeProvider,
                TranscodeProvider.ALIYUN_CLOUD_FUNCTION, TranscodeProvider.ALIYUN_MPS)) {
            try {
                aliyunCdnPrefetch(transcode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //发起软路由预热
//        softRoutePrefetch(transcode);
    }
}
