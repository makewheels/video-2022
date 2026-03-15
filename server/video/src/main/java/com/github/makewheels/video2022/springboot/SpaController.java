package com.github.makewheels.video2022.springboot;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 路由转发：将前端路由转发到 index.html，由 React Router 处理
 */
@Controller
public class SpaController {
    @RequestMapping({
            "/login",
            "/auth/**",
            "/upload",
            "/edit/**",
            "/watch/**",
            "/youtube",
            "/statistics",
            "/my-videos",
            "/channel/**",
            "/settings"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
