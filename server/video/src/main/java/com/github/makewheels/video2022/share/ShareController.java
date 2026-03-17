package com.github.makewheels.video2022.share;

import com.github.makewheels.video2022.system.response.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("share")
@Slf4j
public class ShareController {
    @Resource
    private ShareLinkService shareLinkService;

    @GetMapping("create")
    public Result<ShareLink> create(@RequestParam String videoId) {
        return shareLinkService.createShareLink(videoId);
    }

    @GetMapping("stats")
    public Result<ShareLink> stats(@RequestParam String shortCode) {
        return shareLinkService.getStats(shortCode);
    }

    @GetMapping("/s/{shortCode}")
    public void redirect(
            @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String referrer = request.getHeader("Referer");
        ShareLink shareLink = shareLinkService.resolveShortCode(shortCode, referrer);
        if (shareLink == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String watchId = shareLink.getVideoId();
        response.sendRedirect("/watch/" + watchId);
    }
}
