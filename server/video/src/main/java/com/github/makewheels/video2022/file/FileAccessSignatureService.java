package com.github.makewheels.video2022.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class FileAccessSignatureService {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long SIGNATURE_WINDOW_MS = Duration.ofMinutes(5).toMillis();

    @Value("${file.access.signature-secret}")
    private String signatureSecret;

    public String generateSignature(String videoId, String clientId, String sessionId,
                                    String resolution, String fileId,
                                    String timestamp, String nonce) {
        return hmacSha256(buildPayload(videoId, clientId, sessionId, resolution, fileId, timestamp, nonce));
    }

    public boolean isTimestampValid(String timestamp) {
        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        return Math.abs(System.currentTimeMillis() - requestTimestamp) <= SIGNATURE_WINDOW_MS;
    }

    public boolean isSignatureValid(String videoId, String clientId, String sessionId,
                                    String resolution, String fileId,
                                    String timestamp, String nonce, String sign) {
        if (sign == null || sign.isBlank()) return false;
        String expected = generateSignature(videoId, clientId, sessionId, resolution, fileId, timestamp, nonce);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String buildPayload(String videoId, String clientId, String sessionId,
                                String resolution, String fileId,
                                String timestamp, String nonce) {
        return String.join("\n",
                nullToEmpty(videoId),
                nullToEmpty(clientId),
                nullToEmpty(sessionId),
                nullToEmpty(resolution),
                nullToEmpty(fileId),
                nullToEmpty(timestamp),
                nullToEmpty(nonce)
        );
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute file access signature", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
