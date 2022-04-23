package com.github.makewheels.video2022.cdn;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.cdn20180510.Client;
import com.aliyun.cdn20180510.models.PushObjectCacheRequest;
import com.aliyun.cdn20180510.models.PushObjectCacheResponse;
import com.aliyun.teaopenapi.models.Config;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.M3u8Util;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeProvider;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CdnService {
    @Resource
    private TranscodeRepository transcodeRepository;

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
        log.info("通知软路由预热 " + transcode.getResolution() + ", size = " + urlList.size() + " " + request + request.toJSONString());
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
    private void aliyunCdnPrefetch(Transcode transcode) {
        log.info("阿里云cdn预热 " + transcode.getId());
        List<String> urlList = M3u8Util.getUrlListFromM3u8(transcode.getM3u8CdnUrl());
        log.info("urlList长度 = " + urlList.size());
        Config config = new Config().setAccessKeyId(Base64.decodeStr(aliyunCdnAccessKeyId)).setAccessKeySecret(Base64.decodeStr(aliyunCdnSecretKey));
        config.endpoint = "cdn.aliyuncs.com";
        try {
            Client client = new Client(config);
            PushObjectCacheRequest request = new PushObjectCacheRequest();
            request.setObjectPath(urlList.stream().map(e -> e + "\n").collect(Collectors.joining()));
            PushObjectCacheResponse response = client.pushObjectCache(request);
            log.info("阿里云response = " + JSON.toJSONString(response));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 预热cdn
     *
     * @param transcode
     */
    public void prefetchCdn(Transcode transcode) {
        String transcodeProvider = transcode.getProvider();
        if (StringUtils.equalsAny(transcodeProvider,
                TranscodeProvider.ALIYUN_CLOUD_FUNCTION, TranscodeProvider.ALIYUN_MPS)) {
            aliyunCdnPrefetch(transcode);
        }
        softRoutePrefetch(transcode);
    }
}
