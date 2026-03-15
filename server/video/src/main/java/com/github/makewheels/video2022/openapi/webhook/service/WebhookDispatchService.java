package com.github.makewheels.video2022.openapi.webhook.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookDelivery;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookConfigRepository;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookDeliveryRepository;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class WebhookDispatchService {
    private static final int MAX_ATTEMPTS = 5;
    private static final String HMAC_SHA256 = "HmacSHA256";

    @Resource
    private WebhookConfigRepository webhookConfigRepository;
    @Resource
    private WebhookDeliveryRepository webhookDeliveryRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 向所有订阅了该事件的 webhook 分发事件
     */
    public void dispatchEvent(String appId, String event, Object data) {
        List<WebhookConfig> configs = webhookConfigRepository
                .findByAppIdAndEventsContaining(appId, event);
        if (configs.isEmpty()) {
            log.info("No active webhook configs found for appId={}, event={}", appId, event);
            return;
        }

        JSONObject payloadJson = new JSONObject();
        payloadJson.put("event", event);
        payloadJson.put("timestamp", new Date());
        payloadJson.put("data", data);
        String payload = payloadJson.toJSONString();

        for (WebhookConfig config : configs) {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setWebhookConfigId(config.getId());
            delivery.setEvent(event);
            delivery.setPayload(payload);
            delivery.setStatus("pending");
            delivery.setAttemptCount(0);
            delivery.setCreateTime(new Date());
            webhookDeliveryRepository.save(delivery);

            executorService.submit(() -> sendWithRetry(config, delivery, payload));
        }
    }

    /**
     * 带指数退避重试的 webhook 发送
     */
    private void sendWithRetry(WebhookConfig config, WebhookDelivery delivery, String payload) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            delivery.setAttemptCount(attempt);
            delivery.setLastAttemptTime(new Date());

            try {
                sendWebhook(config, delivery, payload);
                if (delivery.getHttpStatusCode() != null
                        && delivery.getHttpStatusCode() >= 200
                        && delivery.getHttpStatusCode() < 300) {
                    delivery.setStatus("success");
                    webhookDeliveryRepository.save(delivery);
                    log.info("Webhook delivered successfully: configId={}, event={}, attempt={}",
                            config.getId(), delivery.getEvent(), attempt);
                    return;
                }
            } catch (Exception e) {
                log.warn("Webhook delivery failed: configId={}, event={}, attempt={}, error={}",
                        config.getId(), delivery.getEvent(), attempt, e.getMessage());
            }

            webhookDeliveryRepository.save(delivery);

            if (attempt < MAX_ATTEMPTS) {
                try {
                    // 指数退避: 2s, 4s, 8s, 16s
                    long sleepMs = (long) Math.pow(2, attempt) * 1000;
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        delivery.setStatus("failed");
        webhookDeliveryRepository.save(delivery);
        log.error("Webhook delivery exhausted all retries: configId={}, event={}",
                config.getId(), delivery.getEvent());
    }

    /**
     * 发送 webhook HTTP 请求
     */
    private void sendWebhook(WebhookConfig config, WebhookDelivery delivery, String payload) {
        String signature = computeSignature(payload, config.getSecret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", "sha256=" + signature)
                .header("X-Webhook-Event", delivery.getEvent())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            delivery.setHttpStatusCode(response.statusCode());
            delivery.setResponseBody(truncate(response.body(), 2000));
        } catch (Exception e) {
            delivery.setHttpStatusCode(0);
            delivery.setResponseBody(truncate(e.getMessage(), 2000));
            throw new RuntimeException("Webhook HTTP request failed", e);
        }
    }

    /**
     * 计算 HMAC-SHA256 签名
     */
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

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }
}
