package com.github.makewheels.video2022.cdn;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CdnService {
    @Resource
    private TranscodeRepository transcodeRepository;

    @Value("${cdn-prefetch-url}")
    private String cdnPrefetchUrl;
    @Value("${internal-base-url}")
    private String internalBaseUrl;

    /**
     * 软路由预热
     */
    public void softRoutePrefetch(Transcode transcode) {
        JSONObject request = new JSONObject();
        String missionId = IdUtil.getSnowflakeNextIdStr();
        request.put("missionId", missionId + "-" + transcode.getVideoId() + "-" + transcode.getId());
        //组装url list
        String m3u8CdnUrl = transcode.getM3u8CdnUrl();
        String baseUrl = m3u8CdnUrl.substring(0, m3u8CdnUrl.lastIndexOf("/") + 1);
        String[] eachLine = HttpUtil.get(m3u8CdnUrl).split("\n");
        List<String> urlList = new ArrayList<>(eachLine.length + 1);
        urlList.add(m3u8CdnUrl);
        urlList.addAll(Arrays.stream(eachLine)
                .filter(e -> !e.startsWith("#")).map(e -> baseUrl + e)
                .collect(Collectors.toList()));
        request.put("urlList", urlList);
        request.put("callbackUrl", internalBaseUrl + "/cdn/onSoftRoutePrefetchFinish");
        log.info("通知软路由预热 " + transcode.getResolution() + ", size = " + urlList.size()
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
     * @return
     */
    public void aliyunCdnPrefetch(Transcode transcode) {
        log.info("阿里云cdn预热 " + transcode.getId());
    }
}
