package com.github.makewheels.video2022.notification;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("notification")
public class NotificationController {
    @Resource
    private NotificationService notificationService;

    @GetMapping("getMyNotifications")
    public Result<NotificationPageVO> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = UserHolder.getUserId();
        return Result.ok(notificationService.getMyNotifications(userId, page, Math.min(pageSize, 50)));
    }

    @PostMapping("markAsRead")
    public Result<Void> markAsRead(@RequestBody JSONObject body) {
        String userId = UserHolder.getUserId();
        String notificationId = body.getString("notificationId");
        notificationService.markAsRead(userId, notificationId);
        return Result.ok();
    }

    @PostMapping("markAllAsRead")
    public Result<Void> markAllAsRead() {
        String userId = UserHolder.getUserId();
        notificationService.markAllAsRead(userId);
        return Result.ok();
    }

    @GetMapping("getUnreadCount")
    public Result<Integer> getUnreadCount() {
        String userId = UserHolder.getUserId();
        return Result.ok(notificationService.getUnreadCount(userId));
    }
}
