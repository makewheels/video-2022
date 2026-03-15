package com.github.makewheels.video2022.watch.playback;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.watch.playback.dto.ExitPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.HeartbeatPlaybackDTO;
import com.github.makewheels.video2022.watch.playback.dto.StartPlaybackDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("playback")
public class PlaybackController {

    @Autowired
    private PlaybackService playbackService;

    @PostMapping("start")
    public Result<JSONObject> start(@RequestBody StartPlaybackDTO dto) {
        PlaybackSession session = playbackService.startSession(dto);
        JSONObject data = new JSONObject();
        data.put("playbackSessionId", session.getId());
        return Result.ok(data);
    }

    @PostMapping("heartbeat")
    public Result<Void> heartbeat(@RequestBody HeartbeatPlaybackDTO dto) {
        playbackService.heartbeat(dto);
        return Result.ok();
    }

    @PostMapping(value = "exit", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public Result<Void> exit(@RequestBody String body) {
        ExitPlaybackDTO dto = JSON.parseObject(body, ExitPlaybackDTO.class);
        playbackService.exit(dto);
        return Result.ok();
    }
}
