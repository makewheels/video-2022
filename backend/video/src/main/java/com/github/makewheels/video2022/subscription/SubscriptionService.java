package com.github.makewheels.video2022.subscription;

import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubscriptionService {
    @Resource
    private SubscriptionRepository subscriptionRepository;
    @Resource
    private UserRepository userRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    public Result<Void> subscribe(String channelUserId) {
        String userId = UserHolder.getUserId();
        if (userId.equals(channelUserId)) {
            return Result.error("不能订阅自己");
        }
        User channelUser = userRepository.getById(channelUserId);
        if (channelUser == null) {
            return Result.error("频道不存在");
        }
        Subscription existing = subscriptionRepository.findByUserIdAndChannelUserId(userId, channelUserId);
        if (existing != null) {
            return Result.error("已经订阅");
        }
        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setChannelUserId(channelUserId);
        sub.setCreateTime(new Date());
        subscriptionRepository.save(sub);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(channelUserId)),
                new Update().inc("subscriberCount", 1),
                User.class);

        log.info("订阅：userId={}, channelUserId={}", userId, channelUserId);
        return Result.ok();
    }

    public Result<Void> unsubscribe(String channelUserId) {
        String userId = UserHolder.getUserId();
        Subscription existing = subscriptionRepository.findByUserIdAndChannelUserId(userId, channelUserId);
        if (existing == null) {
            return Result.error("未订阅");
        }
        subscriptionRepository.delete(existing);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(channelUserId)),
                new Update().inc("subscriberCount", -1),
                User.class);

        log.info("取消订阅：userId={}, channelUserId={}", userId, channelUserId);
        return Result.ok();
    }

    public boolean isSubscribed(String userId, String channelUserId) {
        return subscriptionRepository.findByUserIdAndChannelUserId(userId, channelUserId) != null;
    }

    public Result<List<String>> getMySubscriptions(int skip, int limit) {
        String userId = UserHolder.getUserId();
        List<Subscription> subs = subscriptionRepository.findByUserId(userId, skip, Math.min(limit, 50));
        List<String> channelIds = subs.stream()
                .map(Subscription::getChannelUserId)
                .collect(Collectors.toList());
        return Result.ok(channelIds);
    }
}
