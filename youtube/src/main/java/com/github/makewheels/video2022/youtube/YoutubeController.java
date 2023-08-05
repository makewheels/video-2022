package com.github.makewheels.video2022.youtube;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("youtube")
@Slf4j
public class YoutubeController {
    @Resource
    private YoutubeService youtubeService;

    @GetMapping("getFileExtension")
    public JSONObject getFileExtension(@RequestParam String youtubeVideoId) {
        log.info("getFileExtension: 请求youtubeVideoId = " + youtubeVideoId);
        String extension = youtubeService.getFileExtension(youtubeVideoId);
        JSONObject response = new JSONObject();
        response.put("extension", extension);
        log.info("getFileExtension: 返回response = " + JSON.toJSONString(response));
        return response;
    }

    @GetMapping("getVideoInfo")
    public JSONObject getVideoInfo(@RequestParam String youtubeVideoId) {
        log.info("getVideoInfo: 请求youtubeVideoId = " + youtubeVideoId);
        JSONObject videoInfo = youtubeService.getVideoInfo(youtubeVideoId);
        log.info("getVideoInfo: 返回videoInfo = " + JSON.toJSONString(videoInfo));
        return videoInfo;
    }

    @PostMapping("transferVideo")
    public JSONObject transferVideo(@RequestBody JSONObject body) {
        log.info("transferVideo: 请求body = " + JSON.toJSONString(body));
        JSONObject jsonObject = youtubeService.transferVideo(body);
        log.info("transferVideo: 返回jsonObject = " + JSON.toJSONString(jsonObject));
        return jsonObject;
    }

    @PostMapping("transferFile")
    public JSONObject transferFile(@RequestBody JSONObject body) {
        log.info("transferFile: 请求body = " + JSON.toJSONString(body));
        JSONObject jsonObject = youtubeService.transferFile(body);
        log.info("transferFile: 返回jsonObject = " + JSON.toJSONString(jsonObject));
        return jsonObject;
    }

}
