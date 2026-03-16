package com.github.makewheels.video2022.developer.service;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import com.github.makewheels.video2022.developer.repository.DeveloperAppRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;

@Service
@Slf4j
public class DeveloperWebhookService {
    private static final String HMAC_SHA256 = "HmacSHA256";

    @Resource
    private DeveloperAppRepository developerAppRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Send a webhook event to a developer app's configured webhook URL.
     * Executed asynchronously.
     */
    @Async
    public void sendWebhookEvent(String appId, String eventType, Object payload) {
        DeveloperApp app = developerAppRepository.findByAppId(appId);
        if (app == null) {
            log.warn("sendWebhookEvent: app not found for appId={}", appId);
            return;
        }
        if (app.getWebhookUrl() == null || app.getWebhookUrl().isEmpty()) {
            log.info("sendWebhookEvent: no webhookUrl configured for appId={}", appId);
            return;
        }
        if (app.getWebhookEvents() == null || !app.getWebhookEvents().contains(eventType)) {
            log.info("sendWebhookEvent: app not subscribed to event={}, appId={}", eventType, appId);
            return;
        }

        JSONObject body = new JSONObject();
        body.put("event", eventType);
        body.put("timestamp", new Date());
        body.put("appId", appId);
        body.put("data", payload);
        String bodyStr = body.toJSONString();

        String signature = computeSignature(bodyStr, app.getWebhookSecret());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(app.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Webhook-Event", eventType)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            log.info("Webhook sent: appId={}, event={}, status={}",
                    appId, eventType, response.statusCode());
        } catch (Exception e) {
            log.error("Webhook delivery failed: appId={}, event={}, error={}",
                    appId, eventType, e.getMessage());
        }
    }

    private String computeSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
