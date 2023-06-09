package com.github.makewheels.video2022.etc.miniprogram;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("miniProgram")
public class MiniProgramController {
    @Resource
    private MiniProgramService miniProgramService;

    /**
     * 获取小程序分享码图片
     */
    @GetMapping("getShareQrCodeUrl")
    public Result<JSONObject> getVideoDetail(@RequestParam String videoId) {
        return miniProgramService.getShareQrCodeUrl(videoId);
    }

    /**
     * 登录
     */
    @GetMapping("login")
    public Result<JSONObject> login(@RequestParam String jscode) {
        return miniProgramService.login(jscode);
    }

}
