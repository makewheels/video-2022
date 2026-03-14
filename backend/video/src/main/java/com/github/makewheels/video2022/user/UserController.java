package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.bean.ChannelVO;
import com.github.makewheels.video2022.user.bean.UpdateProfileRequest;
import com.github.makewheels.video2022.user.bean.User;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

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

    @PostMapping("updateProfile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(request);
    }

    @GetMapping("getChannel")
    public Result<ChannelVO> getChannel(@RequestParam String userId) {
        ChannelVO channel = userService.getChannel(userId);
        if (channel == null) {
            return Result.error("用户不存在");
        }
        return Result.ok(channel);
    }

    @GetMapping("getMyProfile")
    public Result<User> getMyProfile() {
        return Result.ok(userService.getMyProfile());
    }

    /**
     * 创建头像文件记录
     */
    @GetMapping("avatar/createFile")
    public Result<JSONObject> createAvatarFile() {
        return Result.ok(userService.createAvatarFile());
    }

    /**
     * 获取头像上传凭证
     */
    @GetMapping("avatar/getUploadCredentials")
    public Result<JSONObject> getAvatarUploadCredentials(@RequestParam String fileId) {
        return Result.ok(userService.getAvatarUploadCredentials(fileId));
    }

    /**
     * 头像上传完成
     */
    @GetMapping("avatar/uploadFinish")
    public Result<Void> avatarUploadFinish(@RequestParam String fileId) {
        userService.avatarUploadFinish(fileId);
        return Result.ok();
    }

}
