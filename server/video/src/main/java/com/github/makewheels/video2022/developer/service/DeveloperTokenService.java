package com.github.makewheels.video2022.developer.service;

import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import com.github.makewheels.video2022.developer.repository.DeveloperAppRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DeveloperTokenService {
    private static final long TOKEN_EXPIRY_MS = 24 * 3600 * 1000; // 24 hours

    @Value("${developer.jwt-secret}")
    private String jwtSecret;

    @Resource
    private DeveloperAppRepository developerAppRepository;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // Pad key to at least 32 bytes for HS256
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issue a JWT token for a developer app.
     * Validates appId + appSecret, then returns a signed JWT.
     */
    public String createToken(String appId, String appSecret) {
        DeveloperApp app = developerAppRepository.findByAppId(appId);
        if (app == null) {
            throw new RuntimeException("Invalid appId");
        }
        if (!"active".equals(app.getStatus())) {
            throw new RuntimeException("App is not active");
        }
        if (!appSecret.equals(app.getAppSecret())) {
            throw new RuntimeException("Invalid appSecret");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + TOKEN_EXPIRY_MS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("appId", app.getAppId());
        claims.put("userId", app.getUserId());

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Verify a JWT token and return its claims, or null if invalid.
     */
    public Map<String, Object> verifyToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Map<String, Object> result = new HashMap<>();
            result.put("appId", claims.get("appId"));
            result.put("userId", claims.get("userId"));
            result.put("iat", claims.getIssuedAt());
            result.put("exp", claims.getExpiration());
            result.put("valid", true);
            return result;
        } catch (Exception e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            return null;
        }
    }
}
