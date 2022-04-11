package com.github.makewheels.video2022.video;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class YoutubeService {
    @Value("${youtube-service-url}")
    private String youtubeServiceUrl;
    @Value("${base-url}")
    private String baseUrl;

    /**
     * 获取文件拓展名
     *
     * @param youtubeVideoId
     * @return
     */
    public String getFileExtension(String youtubeVideoId) {
        String json = HttpUtil.get(youtubeServiceUrl + "/youtube/getFileExtension" +
                "?youtubeVideoId=" + youtubeVideoId);
        return JSONObject.parseObject(json).getString("extension");
    }

    /**
     * 给 YouTube url，返回视频id
     *
     * @param youtubeUrl
     * @return
     */
    public String getYoutubeVideoId(String youtubeUrl) {
        URI uri = null;
        try {
            uri = new URI(youtubeUrl);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if (uri == null) return null;
        String query = uri.getQuery();
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>(params.length);
        for (String param : params) {
            String[] kv = param.split("=");
            map.put(kv[0], kv[1]);
        }
        String host = uri.getHost();
        //https://www.youtube.com/watch?v=XRWFWB2BP_s&list=PLAhTBeRe8IhMUrLYjdxNCX59YK4Kit43q
        if (host.equals("www.youtube.com")) {
            return map.get("v");
        } else if (host.equals("youtu.be")) {
            //https://youtu.be/tci5eYHwjMc?t=72
            return uri.getPath().substring(1);
        }
        return null;
    }

    /**
     * 提交搬运任务
     *
     * @param video
     * @return
     */
    public JSONObject submitMission(Video video) {
        JSONObject body = new JSONObject();
        body.put("missionId", video.getId());
        body.put("youtubeVideoId", video.getYoutubeVideoId());
        body.put("uploadKey", video.getOriginalFileKey());
        body.put("callbackUrl", baseUrl + "/");
        log.info("提交搬运任务，body = " + body.toJSONString());
        String json = HttpUtil.post(youtubeServiceUrl + "/youtube/submitMission",
                body.toJSONString());
        log.info("海外服务器返回：" + json);
        return JSONObject.parseObject(json);
    }

    /**
     * 获取视频信息
     *
     * @param video
     * @return
     */
    public JSONObject getVideoInfo(Video video) {
        log.info("获取视频信息：youtubeVideoId = " + video.getYoutubeVideoId());
        String json = HttpUtil.get(youtubeServiceUrl + "/youtube/getVideoInfo?youtubeVideoId="
                + video.getYoutubeVideoId());
        log.info("海外服务器返回：" + json);
        return JSONObject.parseObject(json);
    }
}
