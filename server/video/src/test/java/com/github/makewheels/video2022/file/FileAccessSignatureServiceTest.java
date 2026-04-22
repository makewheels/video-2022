package com.github.makewheels.video2022.file;

import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class FileAccessSignatureServiceTest extends BaseIntegrationTest {

    @Autowired
    private FileAccessSignatureService fileAccessSignatureService;

    @Test
    void generateSignature_andValidate_roundTripSucceeds() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = fileAccessSignatureService.generateSignature(
                "v_sign_001",
                "client_sign_001",
                "session_sign_001",
                "720p",
                "f_ts_sign_001",
                timestamp,
                "nonce-sign-001"
        );

        assertTrue(fileAccessSignatureService.isTimestampValid(timestamp));
        assertTrue(fileAccessSignatureService.isSignatureValid(
                "v_sign_001",
                "client_sign_001",
                "session_sign_001",
                "720p",
                "f_ts_sign_001",
                timestamp,
                "nonce-sign-001",
                sign
        ));
    }

    @Test
    void isTimestampValid_expiredTimestampReturnsFalse() {
        String expiredTimestamp = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L);

        assertFalse(fileAccessSignatureService.isTimestampValid(expiredTimestamp));
    }
}
