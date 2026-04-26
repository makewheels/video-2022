package com.github.makewheels.video2022.openapi.oauth.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.repository.OAuthAppRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class OAuthAppService {
    @Resource
    private OAuthAppRepository oAuthAppRepository;

    /**
     * 创建OAuth应用，返回时clientSecretHash字段临时存放明文secret（仅展示一次）
     */
    public OAuthApp createApp(String developerId, String name, String description,
                              List<String> redirectUris, List<String> scopes) {
        String clientId = IdUtil.simpleUUID();
        String clientSecret = IdUtil.simpleUUID();

        OAuthApp app = new OAuthApp();
        app.setDeveloperId(developerId);
        app.setName(name);
        app.setDescription(description);
        app.setClientId(clientId);
        app.setClientSecretHash(BCrypt.hashpw(clientSecret, BCrypt.gensalt()));
        app.setRedirectUris(redirectUris);
        app.setScopes(scopes);

        oAuthAppRepository.save(app);

        // 临时将明文secret放入hash字段用于返回（不会再次持久化）
        app.setClientSecretHash(clientSecret);
        return app;
    }

    public List<OAuthApp> getAppsByDeveloperId(String developerId) {
        return oAuthAppRepository.findByDeveloperId(developerId);
    }

    public OAuthApp getById(String appId) {
        return oAuthAppRepository.getById(appId);
    }

    public OAuthApp getByClientId(String clientId) {
        return oAuthAppRepository.findByClientId(clientId);
    }

    public void deleteApp(String developerId, String appId) {
        OAuthApp app = oAuthAppRepository.getById(appId);
        if (app == null) {
            throw new RuntimeException("App not found");
        }
        if (!developerId.equals(app.getDeveloperId())) {
            throw new RuntimeException("App does not belong to this developer");
        }
        oAuthAppRepository.deleteById(appId);
    }

    /**
     * 校验client credentials，成功返回OAuthApp，失败返回null
     */
    public OAuthApp validateClientCredentials(String clientId, String clientSecret) {
        OAuthApp app = oAuthAppRepository.findByClientId(clientId);
        if (app == null) {
            return null;
        }
        if (!"active".equals(app.getStatus())) {
            return null;
        }
        if (!BCrypt.checkpw(clientSecret, app.getClientSecretHash())) {
            return null;
        }
        return app;
    }
}
