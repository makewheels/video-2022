package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Users", description = "用户信息")
@RestController
@RequestMapping("/api/v1/users")
@Slf4j
public class ApiUserController {
    @Resource
    private ApiAuthHelper apiAuthHelper;
    @Resource
    private UserRepository userRepository;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        String userId = apiAuthHelper.requireUserId();
        User user = userRepository.getById(userId);
        if (user == null) {
            return Result.error(ErrorCode.USER_NOT_EXIST);
        }
        // 脱敏：不暴露 token 和完整手机号
        user.setToken(null);
        if (user.getPhone() != null && user.getPhone().length() >= 7) {
            user.setPhone(user.getPhone().substring(0, 3) + "****"
                    + user.getPhone().substring(user.getPhone().length() - 4));
        }
        return Result.ok(user);
    }

    @Operation(summary = "获取用户公开信息")
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getUser(@PathVariable String userId) {
        User user = userRepository.getById(userId);
        if (user == null) {
            return Result.error(ErrorCode.USER_NOT_EXIST);
        }
        Map<String, Object> publicInfo = new LinkedHashMap<>();
        publicInfo.put("id", user.getId());
        publicInfo.put("nickname", user.getNickname());
        publicInfo.put("avatarUrl", user.getAvatarUrl());
        publicInfo.put("bannerUrl", user.getBannerUrl());
        publicInfo.put("bio", user.getBio());
        publicInfo.put("subscriberCount", user.getSubscriberCount());
        publicInfo.put("videoCount", user.getVideoCount());
        return Result.ok(publicInfo);
    }
}
