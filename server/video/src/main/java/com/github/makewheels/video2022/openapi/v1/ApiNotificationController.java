package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.notification.NotificationPageVO;
import com.github.makewheels.video2022.notification.NotificationService;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications", description = "Notification Management")
@RestController
@RequestMapping("/api/v1/notifications")
public class ApiNotificationController {
    @Resource
    private ApiAuthHelper apiAuthHelper;
    @Resource
    private NotificationService notificationService;

    @Operation(summary = "Get notifications")
    @GetMapping
    public Result<NotificationPageVO> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            apiAuthHelper.setupUserContext();
            String userId = apiAuthHelper.requireUserId();
            return Result.ok(notificationService.getMyNotifications(userId, page, Math.min(size, 50)));
        } finally {
            apiAuthHelper.clearUserContext();
        }
    }
}
