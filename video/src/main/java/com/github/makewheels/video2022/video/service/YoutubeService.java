package com.github.makewheels.video2022.video.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class YoutubeService {
    @Resource
    private EnvironmentService environmentService;

    /**
     * 获取文件拓展名
     */
    public String getFileExtension(String youtubeVideoId) {
//        String json = HttpUtil.get(youtubeServiceUrl + "/youtube/getFileExtension" +
//                "?youtubeVideoId=" + youtubeVideoId);
//        return JSONObject.parseObject(json).getString("extension");
        return "webm";
    }

    /**
     * 给 YouTube url，返回视频id
     */
    public String getYoutubeVideoIdByUrl(String youtubeUrl) {
        URI uri = null;
        try {
            uri = new URI(youtubeUrl);
        } catch (URISyntaxException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        if (uri == null) return null;
        String host = uri.getHost();
        //https://www.youtube.com/watch?v=XRWFWB2BP_s&list=PLAhTBeRe8IhMUrLYjdxNCX59YK4Kit43q
        if (host.equals("www.youtube.com")) {
            String query = uri.getQuery();
            String[] params = query.split("&");
            Map<String, String> map = new HashMap<>(params.length);
            for (String param : params) {
                String[] kv = param.split("=");
                map.put(kv[0], kv[1]);
            }
            return map.get("v");
        } else if (host.equals("youtu.be")) {
            //https://youtu.be/tci5eYHwjMc?t=72
            return uri.getPath().substring(1);
        }
        return null;
    }

    /**
     * 获取视频信息
     */
    public JSONObject getVideoInfo(String youtubeVideoId) {
        log.info("获取YouTube视频信息：youtubeVideoId = " + youtubeVideoId);
        String json = HttpUtil.get(environmentService.getYoutubeServiceUrl()
                + "/youtube/getVideoInfo?youtubeVideoId="
                + youtubeVideoId);
        log.info("获取YouTube视频信息，海外服务器返回：" + json);
        return JSONObject.parseObject(json);
    }

    /**
     * 提交搬运任务
     */
    public JSONObject transferVideo(User user, Video video, File file) {
        JSONObject body = new JSONObject();
        body.put("missionId", IdUtil.nanoId());
        body.put("youtubeVideoId", video.getYouTube().getVideoId());
        body.put("key", file.getKey());
        body.put("provider", file.getProvider());
        body.put("fileId", file.getId());
        body.put("watchId", video.getWatch().getWatchId());
        body.put("videoId", video.getId());

        //获取文件上传凭证地址
        body.put("getUploadCredentialsUrl", environmentService.getCallbackUrl(
                "/file/getUploadCredentials?fileId=" + file.getId() + "&token=" + user.getToken()));

        //文件上传完成回调地址
        body.put("fileUploadFinishCallbackUrl", environmentService.getCallbackUrl(
                "/file/uploadFinish?fileId=" + file.getId() + "&token=" + user.getToken()));

        //通知业务原始文件上传完成回调地址
        body.put("businessUploadFinishCallbackUrl", environmentService.getCallbackUrl(
                "/video/rawFileUploadFinish?videoId=" + video.getId() + "&token=" + user.getToken()));

        log.info("提交搬运视频任务，body = " + body.toJSONString());

        // 调用阿里云香港云函数
        String url = environmentService.getYoutubeServiceUrl() + "/youtube/transferVideo";
        String json = HttpUtil.createPost(url)
                // 异步调用
                .header("X-Fc-Invocation-Type", "Async")
                .body(body.toJSONString())
                .execute().body();
        log.info("提交搬运视频任务，海外服务器返回：" + json);
        return JSONObject.parseObject(json);
    }

    /**
     * 搬运文件到国内对象存储
     */
    public JSONObject transferFile(
            User user, File file, String downloadUrl, String businessUploadFinishCallbackUrl) {
        JSONObject body = new JSONObject();
        body.put("missionId", IdUtil.nanoId());
        body.put("key", file.getKey());
        body.put("provider", file.getProvider());
        body.put("fileId", file.getId());
        body.put("downloadUrl", downloadUrl);

        //获取文件上传凭证地址
        body.put("getUploadCredentialsUrl", environmentService.getCallbackUrl(
                "/file/getUploadCredentials?fileId=" + file.getId() + "&token=" + user.getToken()));

        //文件上传完成回调地址
        body.put("fileUploadFinishCallbackUrl", environmentService.getCallbackUrl(
                "/file/uploadFinish?fileId=" + file.getId() + "&token=" + user.getToken()));

        //通知业务原始文件上传完成回调地址
        body.put("businessUploadFinishCallbackUrl", businessUploadFinishCallbackUrl);

        log.info("提交搬运文件任务，body = " + body.toJSONString());
        String url = environmentService.getYoutubeServiceUrl() + "/youtube/transferFile";
        String json = HttpUtil.createPost(url)
                // 异步调用
                .header("X-Fc-Invocation-Type", "Async")
                .body(body.toJSONString())
                .execute().body();
        log.info("提交搬运文件任务，海外服务器返回：" + json);
        return JSONObject.parseObject(json);
    }


}
