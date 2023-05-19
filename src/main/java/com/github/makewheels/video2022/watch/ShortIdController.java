package com.github.makewheels.video2022.watch;

import com.github.makewheels.video2022.environment.EnvironmentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ShortIdController {
    private EnvironmentService environmentService;

    //    @GetMapping("/{shortId}")
    public String toWatchPage(@PathVariable("shortId") String shortId) {
        return "redirect:" + environmentService.getInternalBaseUrl() + "/watch?v=" + shortId;
    }
}
