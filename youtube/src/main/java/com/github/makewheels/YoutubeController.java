package com.github.makewheels;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("youtube")
public class YoutubeController {
    @Resource
    private YoutubeService youtubeService;

    @GetMapping("getFileExtension")
    public JSONObject getFileExtension(@RequestParam String youtubeVideoId) {
        String extension = youtubeService.getFileExtension(youtubeVideoId);
        JSONObject response = new JSONObject();
        response.put("extension", extension);
        return response;
    }

    @GetMapping("getVideoInfo")
    public JSONObject getVideoInfo(@RequestParam String youtubeVideoId) {
        return youtubeService.getVideoInfo(youtubeVideoId);
    }

    @PostMapping("transferVideo")
    public JSONObject transferVideo(@RequestBody JSONObject body) {
        return youtubeService.transferVideo(body);
    }

    @PostMapping("transferFile")
    public JSONObject transferFile(@RequestBody JSONObject body) {
        return youtubeService.transferFile(body);
    }

}
