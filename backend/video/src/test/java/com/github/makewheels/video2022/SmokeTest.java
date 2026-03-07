package com.github.makewheels.video2022;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test to verify the Spring context loads successfully with test profile.
 */
public class SmokeTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
        assertNotNull(mongoTemplate, "MongoTemplate should be injected");
    }
}
