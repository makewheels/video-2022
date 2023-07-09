package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.user;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.redis.RedisKey;
import com.github.makewheels.video2022.redis.RedisService;
import com.github.makewheels.video2022.redis.RedisTime;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class UserRedisService {
    @Resource
    private RedisService redisService;

    public VerificationCode getVerificationCode(String phone) {
        String json = (String) redisService.get(RedisKey.verificationCode(phone));
        return JSON.parseObject(json, VerificationCode.class);
    }

    public VerificationCode setVerificationCode(String phone, String code) {
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setPhone(phone);
        verificationCode.setCode(code);
        redisService.set(RedisKey.verificationCode(phone),
                JSON.toJSONString(verificationCode), RedisTime.TEN_MINUTES);
        return verificationCode;
    }

    public void delVerificationCode(String phone) {
        redisService.del(RedisKey.verificationCode(phone));
    }

    public User getUserByToken(String token) {
        String json = (String) redisService.get(RedisKey.token(token));
        return JSON.parseObject(json, User.class);
    }

    public void setUserByToken(User user) {
        redisService.set(RedisKey.token(user.getToken()), JSON.toJSONString(user),
                RedisTime.THIRTY_MINUTES);
    }

    public void delUserByToken(String token) {
        redisService.del(RedisKey.token(token));
    }
}
