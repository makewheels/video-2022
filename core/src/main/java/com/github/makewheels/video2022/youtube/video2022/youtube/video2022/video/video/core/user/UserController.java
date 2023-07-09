package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.user;

import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("user")
public class UserController {
    @Resource
    private UserService userService;

    @GetMapping("requestVerificationCode")
    public Result<Void> requestVerificationCode(@RequestParam String phone) {
        userService.requestVerificationCode(phone);
        return Result.ok();
    }

    @GetMapping("submitVerificationCode")
    public Result<User> submitVerificationCode(@RequestParam String phone, @RequestParam String code) {
        User user = userService.submitVerificationCode(phone, code);
        return Result.ok(user);
    }

    @GetMapping("getUserByToken")
    public Result<User> getUserByToken(@RequestParam String token) {
        User user = userService.getUserByToken(token);
        if (user != null) {
            return Result.ok(user);
        } else {
            return Result.error(ErrorCode.USER_TOKEN_WRONG);
        }
    }

    @GetMapping("getUserById")
    public Result<User> getUserById(@RequestParam String userId) {
        User user = userService.getUserById(userId);
        return Result.ok(user);
    }

}
