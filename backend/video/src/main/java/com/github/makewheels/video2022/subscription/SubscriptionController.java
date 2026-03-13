package com.github.makewheels.video2022.subscription;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("subscription")
public class SubscriptionController {
    @Resource
    private SubscriptionService subscriptionService;

    @GetMapping("subscribe")
    public Result<Void> subscribe(@RequestParam String channelUserId) {
        return subscriptionService.subscribe(channelUserId);
    }

    @GetMapping("unsubscribe")
    public Result<Void> unsubscribe(@RequestParam String channelUserId) {
        return subscriptionService.unsubscribe(channelUserId);
    }

    @GetMapping("getStatus")
    public Result<Boolean> getStatus(@RequestParam String channelUserId) {
        String userId = UserHolder.getUserId();
        boolean subscribed = subscriptionService.isSubscribed(userId, channelUserId);
        return Result.ok(subscribed);
    }

    @GetMapping("getMySubscriptions")
    public Result<List<String>> getMySubscriptions(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return subscriptionService.getMySubscriptions(skip, limit);
    }
}
