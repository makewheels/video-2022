package com.github.makewheels.video2022.user;

import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.bean.User;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("user")
public class UserController {
    @Resource
    private UserService userService;

    @GetMapping("requestVerificationCode")
    public Result<Void> requestVerificationCode(@RequestParam String phone) {
        return userService.requestVerificationCode(phone);
    }

    @GetMapping("submitVerificationCode")
    public Result<User> submitVerificationCode(@RequestParam String phone, @RequestParam String code) {
        return userService.submitVerificationCode(phone, code);
    }

    @GetMapping("getUserByToken")
    public Result<User> getUserByToken(@RequestParam String token) {
        return Result.ok(userService.getUserByToken(token));
    }

    @GetMapping("getUserById")
    public Result<User> getUserById(@RequestParam String userId) {
        return userService.getUserById(userId);
    }

}
