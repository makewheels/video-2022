package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.openapi.oauth.OAuthContext;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 桥接 OAuth 上下文与现有 UserHolder 体系
 */
@Component
public class ApiAuthHelper {
    @Resource
    private UserRepository userRepository;

    /**
     * 从 OAuthContext 获取 userId，查找 User 并设置到 UserHolder。
     * 调用方在业务完成后必须调用 {@link #clearUserContext()} 清理。
     */
    public User setupUserContext() {
        String userId = OAuthContext.getCurrentUserId();
        if (userId == null) {
            throw new VideoException(ErrorCode.USER_NOT_LOGIN, "OAuth token 未关联用户");
        }
        User user = userRepository.getById(userId);
        if (user == null) {
            throw new VideoException(ErrorCode.USER_NOT_EXIST, "用户不存在, userId = " + userId);
        }
        UserHolder.set(user);
        return user;
    }

    /**
     * 清理 UserHolder，防止 ThreadLocal 泄漏
     */
    public void clearUserContext() {
        UserHolder.remove();
    }

    /**
     * 获取当前 OAuth 用户 ID（可能为 null）
     */
    public String getCurrentUserId() {
        return OAuthContext.getCurrentUserId();
    }

    /**
     * 要求当前必须存在用户上下文，否则抛异常
     */
    public String requireUserId() {
        String userId = OAuthContext.getCurrentUserId();
        if (userId == null) {
            throw new VideoException(ErrorCode.USER_NOT_LOGIN, "OAuth token 未关联用户");
        }
        return userId;
    }
}
