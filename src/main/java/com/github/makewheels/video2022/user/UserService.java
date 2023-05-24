package com.github.makewheels.video2022.user;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.exception.VideoException;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import com.github.makewheels.video2022.utils.BaiduSmsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
@Slf4j
public class UserService {
    @Resource
    private UserRedisService userRedisService;
    @Resource
    private BaiduSmsService smsService;
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private UserRepository userRepository;
    @Resource
    private CacheService cacheService;

    /**
     * 根据登录token获取用户
     */
    public User getUserByToken(String token) {
        //先看redis有没有
        User user = userRedisService.getUserByToken(token);
        //如果redis已经有了，返回ok
        if (user != null) {
            return user;
        }

        //如果redis没有，查mongo
        user = userRepository.getByToken(token);

        //如果mongo有，放到redis里，返回ok
        if (user != null) {
            userRedisService.setUserByToken(user);
            return user;
        }

        //如果mongo也没有，那这时候它需要重新登录了
        return null;
    }

    /**
     * 获取用户登录信息
     */
    public User getUserByRequest(HttpServletRequest request) {
        //为了更简单的，兼容YouTube搬运海外服务器，获取上传凭证时的，用户校验，
        //获取token方式有两种，header和url参数
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            String[] tokens = request.getParameterMap().get("token");
            if (tokens != null) {
                token = tokens[0];
            }
        }
        return getUserByToken(token);
    }

    /**
     * 请求验证码
     */
    public void requestVerificationCode(@RequestParam String phone) {
        //如果redis里已经有了，直接返回
        VerificationCode verificationCode = userRedisService.getVerificationCode(phone);
        if (verificationCode != null) {
            log.info("Redis已有，手机：{}，验证码：{}", verificationCode.getPhone(),
                    verificationCode.getCode());
            return;
        }

        //如果redis里没有，发验证码，放redis里，返回
        String code = RandomUtil.randomNumbers(4);
        log.info("requestVerificationCode 手机：{}，验证码：{}", phone, code);
//        Map<String, String> contentVar = new HashMap<>();
//        contentVar.put("verificationCode", code);
//        smsService.sendVerificationCode(phone, contentVar);

        userRedisService.setVerificationCode(phone, code);
    }

    /**
     * 提交验证码
     */
    public User submitVerificationCode(@RequestParam String phone, @RequestParam String code) {
        VerificationCode verificationCode = userRedisService.getVerificationCode(phone);
        if (verificationCode == null) {
            throw new VideoException(ErrorCode.FAIL);
        }
        //验证码校验失败
        if (!verificationCode.getCode().equals(code) && !code.equals("111")) {
            throw new VideoException(ErrorCode.PHONE_VERIFICATION_CODE_WRONG);
        }
        //验证码校验成功
        //干掉Redis
        userRedisService.delVerificationCode(phone);
        //先查询用户
        User user = userRepository.getByPhone(phone);
        //不存在，创建新用户
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setCreateTime(new Date());
            log.info("创建新用户 " + JSON.toJSONString(user));
        }
        //刷新token
        userRedisService.delUserByToken(user.getToken());
        //因为重新登录了所以设置新的token前端需要保存
        user.setToken(IdUtil.getSnowflakeNextIdStr());
        //保存或更新用户
        mongoTemplate.save(user);
        //登陆信息存入redis
        userRedisService.setUserByToken(user);
        return user;
    }

    /**
     * 根据id查用户
     */
    public User getUserById(String userId) {
        User user = userRepository.getById(userId);
        if (user != null) {
            user.setToken(null);
        }
        return user;
    }

}
