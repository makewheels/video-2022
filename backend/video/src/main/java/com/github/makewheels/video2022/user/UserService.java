package com.github.makewheels.video2022.user;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.finance.wallet.WalletService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import com.github.makewheels.video2022.user.bean.ChannelVO;
import com.github.makewheels.video2022.user.bean.UpdateProfileRequest;
import com.github.makewheels.video2022.utils.IdService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
@Slf4j
public class UserService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private UserRepository userRepository;
    @Resource
    private IdService idService;
    @Resource
    private WalletService walletService;
    @Resource
    private EnvironmentService environmentService;

    public User getUserByToken(String token) {
        if (token == null) {
            return null;
        }
        return userRepository.getByToken(token);
    }

    public User getUserByRequest(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            String[] tokens = request.getParameterMap().get("token");
            if (tokens != null) {
                token = tokens[0];
            }
        }
        return getUserByToken(token);
    }

    public void requestVerificationCode(String phone) {
        VerificationCode existing = mongoTemplate.findOne(
                new Query(Criteria.where("phone").is(phone)), VerificationCode.class);
        if (existing != null) {
            log.info("MongoDB已有，手机：{}，验证码已存在", phone);
            return;
        }

        String code = RandomUtil.randomNumbers(4);
        log.info("requestVerificationCode 手机：{}，验证码已发送", phone);

        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setPhone(phone);
        verificationCode.setCode(code);
        verificationCode.setCreatedAt(new Date());
        mongoTemplate.save(verificationCode);
    }

    public User submitVerificationCode(String phone, String code) {
        VerificationCode verificationCode = mongoTemplate.findOne(
                new Query(Criteria.where("phone").is(phone)), VerificationCode.class);
        if (verificationCode == null) {
            throw new VideoException(ErrorCode.USER_PHONE_VERIFICATION_CODE_EXPIRED);
        }
        boolean codeMatches = verificationCode.getCode().equals(code);
        boolean isDevBypass = !environmentService.isProductionEnv() && code.equals("111");
        if (!codeMatches && !isDevBypass) {
            throw new VideoException(ErrorCode.USER_PHONE_VERIFICATION_CODE_WRONG);
        }
        // Delete verification code from MongoDB
        mongoTemplate.remove(new Query(Criteria.where("phone").is(phone)), VerificationCode.class);

        User user = userRepository.getByPhone(phone);
        if (user == null) {
            user = new User();
            user.setId(idService.getUserId());
            user.setPhone(phone);
            log.info("创建新用户 " + user);
        }
        user.setToken(IdUtil.getSnowflakeNextIdStr());
        mongoTemplate.save(user);
        walletService.createWallet(user.getId());
        return user;
    }

    public User getUserById(String userId) {
        User user = userRepository.getById(userId);
        if (user != null) {
            user.setToken(null);
        }
        return user;
    }

    public Result<Void> updateProfile(UpdateProfileRequest request) {
        String userId = UserHolder.getUserId();
        String nickname = request.getNickname();
        String bio = request.getBio();

        if (nickname != null && nickname.length() > 30) {
            return Result.error("昵称不能超过30字");
        }
        if (bio != null && bio.length() > 200) {
            return Result.error("简介不能超过200字");
        }

        userRepository.updateProfile(userId, nickname, bio);
        return Result.ok();
    }

    public ChannelVO getChannel(String channelUserId) {
        User user = userRepository.getById(channelUserId);
        if (user == null) {
            return null;
        }
        ChannelVO vo = new ChannelVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setBannerUrl(user.getBannerUrl());
        vo.setBio(user.getBio());
        vo.setSubscriberCount(user.getSubscriberCount() != null ? user.getSubscriberCount() : 0L);
        vo.setVideoCount(user.getVideoCount() != null ? user.getVideoCount() : 0L);
        vo.setIsSubscribed(false);
        return vo;
    }

    public User getMyProfile() {
        User user = userRepository.getById(UserHolder.getUserId());
        if (user != null) {
            user.setToken(null);
        }
        return user;
    }
}
