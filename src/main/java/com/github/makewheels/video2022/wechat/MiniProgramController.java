package com.github.makewheels.video2022.wechat;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.user.UserServiceClient;
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
    private UserServiceClient userServiceClient;
    @Resource
    private MiniProgramService miniProgramService;

    /**
     * 获取小程序分享码图片
     */
    @GetMapping("getShareQrCodeUrl")
    public Result<JSONObject> getVideoDetail(HttpServletRequest request, @RequestParam String videoId) {
        User user = userServiceClient.getUserByRequest(request);
        return miniProgramService.getShareQrCodeUrl(user, videoId);
    }

}
