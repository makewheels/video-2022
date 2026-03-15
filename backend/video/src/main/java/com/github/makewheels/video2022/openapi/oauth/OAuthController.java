package com.github.makewheels.video2022.openapi.oauth;

import com.github.makewheels.video2022.openapi.oauth.dto.OAuthTokenResponse;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthToken;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthTokenService;
import com.github.makewheels.video2022.system.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;

@RestController
@RequestMapping("oauth")
@Slf4j
public class OAuthController {
    @Resource
    private OAuthAppService oAuthAppService;

    @Resource
    private OAuthTokenService oAuthTokenService;

    /**
     * OAuth token端点，支持client_credentials和refresh_token两种grant_type
     */
    @PostMapping("token")
    public Result<OAuthTokenResponse> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request) {

        if ("client_credentials".equals(grantType)) {
            return handleClientCredentials(clientId, clientSecret, request);
        } else if ("refresh_token".equals(grantType)) {
            return handleRefreshToken(refreshToken);
        } else {
            return Result.error("Unsupported grant_type: " + grantType);
        }
    }

    /**
     * 撤销token
     */
    @PostMapping("revoke")
    public Result<Void> revoke(@RequestParam String token) {
        oAuthTokenService.revokeToken(token);
        return Result.ok();
    }

    private Result<OAuthTokenResponse> handleClientCredentials(
            String clientId, String clientSecret, HttpServletRequest request) {
        // 优先从Basic Auth获取凭据
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                clientId = parts[0];
                clientSecret = parts[1];
            }
        }

        if (clientId == null || clientSecret == null) {
            return Result.error("client_id and client_secret are required");
        }

        OAuthApp app = oAuthAppService.validateClientCredentials(clientId, clientSecret);
        if (app == null) {
            return Result.error("Invalid client credentials");
        }

        OAuthToken token = oAuthTokenService.issueToken(app.getId(), null, app.getScopes());
        return Result.ok(buildTokenResponse(token));
    }

    private Result<OAuthTokenResponse> handleRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            return Result.error("refresh_token is required");
        }
        OAuthToken token = oAuthTokenService.refreshToken(refreshToken);
        return Result.ok(buildTokenResponse(token));
    }

    private OAuthTokenResponse buildTokenResponse(OAuthToken token) {
        OAuthTokenResponse response = new OAuthTokenResponse();
        response.setAccessToken(token.getAccessToken());
        response.setRefreshToken(token.getRefreshToken());
        response.setTokenType("Bearer");
        response.setExpiresIn((token.getExpiresAt().getTime() - System.currentTimeMillis()) / 1000);
        if (token.getScopes() != null) {
            response.setScope(String.join(" ", token.getScopes()));
        }
        return response;
    }
}
