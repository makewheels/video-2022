package com.github.makewheels.video2022.video;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class YoutubeService {
    @Value("${youtube-service-url}")
    private String youtubeServiceUrl;

    public String getFileExtension(String youtubeVideoId) {
        String json = HttpUtil.get(youtubeServiceUrl + "/getFileExtension?youtubeVideoId=" + youtubeVideoId);
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
}
