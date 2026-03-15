package com.github.makewheels.video2022.cover;

import com.github.makewheels.video2022.system.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("cover")
@Slf4j
public class CoverController {
    @Resource
    private CoverCallbackService coverCallbackService;

    @Value("${callback.secret}")
    private String callbackSecret;

    /**
     * youtube封面完成回调
     */
    @GetMapping("youtubeUploadFinishCallback")
    public ResponseEntity<Result<Void>> youtubeUploadFinishCallback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestParam String coverId) {
        if (callbackSecret == null || !callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.debug("收到youtube封面完成回调：coverId = {}", coverId);
        return ResponseEntity.ok(coverCallbackService.youtubeUploadFinishCallback(coverId));
    }

}
