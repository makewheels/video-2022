package com.github.makewheels.video2022.cdn;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
    public void softRoutePrefetch(String videoId) {
        log.info("预热到软路由，videoId = " + videoId);
        //通知预加载缓存
        JSONObject request = new JSONObject();
        String missionId = IdUtil.getSnowflakeNextIdStr();
        request.put("missionId", missionId);
        List<String> urlList = new ArrayList<>();
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        transcodeList.forEach(transcode -> {
            String m3u8CdnUrl = transcode.getM3u8CdnUrl();
            String baseUrl = m3u8CdnUrl.substring(0, m3u8CdnUrl.lastIndexOf("/") + 1);
            String[] eachLine = HttpUtil.get(m3u8CdnUrl).split("\n");
            for (String line : eachLine) {
                if (line.startsWith("#")) continue;
                urlList.add(baseUrl + line);
            }
        });
        request.put("urlList", urlList);
        request.put("callbackUrl", internalBaseUrl + "/cdn/onSoftRoutePrefetchFinish");
        log.info("通知预热cdn, missionId = " + missionId + ", size = " + urlList.size());
        log.info(cdnPrefetchUrl);
        log.info(request.toJSONString());
        String response = HttpUtil.post(cdnPrefetchUrl, request.toJSONString());
        log.info("请求预热，软路由回复：");
        log.info(response);
    }

    /**
     * 在cdn预热完成时
     *
     * @param body
     * @return
     */
    public Result<Void> onSoftRoutePrefetchFinish(JSONObject body) {
        log.info("收到软路由预热完成回调：" + body.toJSONString());
        return Result.ok();
    }
}
