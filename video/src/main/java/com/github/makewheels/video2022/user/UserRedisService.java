package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.redis.RedisKey;
import com.github.makewheels.video2022.etc.redis.RedisService;
import com.github.makewheels.video2022.etc.redis.RedisTime;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class UserRedisService {
    @Resource
    private RedisService redisService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

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
        String json = (String) redisTemplate.opsForValue().get(token);
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
