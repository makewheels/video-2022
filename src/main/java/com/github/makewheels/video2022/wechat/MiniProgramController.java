package com.github.makewheels.video2022.wechat;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("miniProgram")
public class MiniProgramController {
    @Resource
    private UserService userService;
    @Resource
    private MiniProgramService miniProgramService;

    /**
     * 获取小程序分享码图片
     */
    @GetMapping("getShareQrCodeUrl")
    public Result<JSONObject> getVideoDetail(HttpServletRequest request, @RequestParam String videoId) {
        User user = userService.getUserByRequest(request);
        return miniProgramService.getShareQrCodeUrl(user, videoId);
    }

    /**
     * 登录
     */
    @GetMapping("login")
    public Result<JSONObject> login(HttpServletRequest request, @RequestParam String jscode) {
        User user = userService.getUserByRequest(request);
        return miniProgramService.login(user,jscode);
    }

}
