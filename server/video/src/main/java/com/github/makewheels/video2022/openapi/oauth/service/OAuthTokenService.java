package com.github.makewheels.video2022.openapi.oauth.service;

import cn.hutool.core.util.IdUtil;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthToken;
import com.github.makewheels.video2022.openapi.oauth.repository.OAuthTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class OAuthTokenService {
    private static final long TOKEN_EXPIRY_MS = 2 * 3600 * 1000L;

    @Resource
    private OAuthTokenRepository oAuthTokenRepository;

    /**
     * 签发token，有效期2小时
     */
    public OAuthToken issueToken(String appId, String userId, List<String> scopes) {
        OAuthToken token = new OAuthToken();
        token.setAppId(appId);
        token.setUserId(userId);
        token.setAccessToken(IdUtil.simpleUUID());
        token.setRefreshToken(IdUtil.simpleUUID());
        token.setScopes(scopes);
        token.setExpiresAt(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS));
        return oAuthTokenRepository.save(token);
    }

    /**
     * 校验accessToken，过期或不存在返回null
     */
    public OAuthToken validateAccessToken(String accessToken) {
        OAuthToken token = oAuthTokenRepository.findByAccessToken(accessToken);
        if (token == null) {
            return null;
        }
        if (token.getExpiresAt().before(new Date())) {
            return null;
        }
        return token;
    }

    /**
     * 刷新token，删除旧token并签发新token
     */
    public OAuthToken refreshToken(String refreshToken) {
        OAuthToken oldToken = oAuthTokenRepository.findByRefreshToken(refreshToken);
        if (oldToken == null) {
            throw new RuntimeException("Invalid refresh token");
        }
        oAuthTokenRepository.deleteByAccessToken(oldToken.getAccessToken());
        return issueToken(oldToken.getAppId(), oldToken.getUserId(), oldToken.getScopes());
    }

    /**
     * 撤销token
     */
    public void revokeToken(String accessToken) {
        oAuthTokenRepository.deleteByAccessToken(accessToken);
    }
}
