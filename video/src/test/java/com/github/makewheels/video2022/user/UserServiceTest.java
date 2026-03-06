package com.github.makewheels.video2022.user;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.etc.redis.RedisKey;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest extends BaseIntegrationTest {

    private static final String TEST_PHONE = "13800000001";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRedisService userRedisService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        cleanRedisKeys("video-2022:*");
    }

    // ---- requestVerificationCode ----

    @Test
    void requestVerificationCode_shouldStoreCodeInRedis() {
        userService.requestVerificationCode(TEST_PHONE);

        VerificationCode stored = userRedisService.getVerificationCode(TEST_PHONE);
        assertNotNull(stored, "Verification code should be stored in Redis");
        assertEquals(TEST_PHONE, stored.getPhone());
        assertNotNull(stored.getCode());
        assertEquals(4, stored.getCode().length(), "Code should be 4 digits");
    }

    @Test
    void requestVerificationCode_calledTwice_shouldReturnSameCode() {
        userService.requestVerificationCode(TEST_PHONE);
        VerificationCode first = userRedisService.getVerificationCode(TEST_PHONE);

        userService.requestVerificationCode(TEST_PHONE);
        VerificationCode second = userRedisService.getVerificationCode(TEST_PHONE);

        assertEquals(first.getCode(), second.getCode(),
                "Calling twice should return the same code (already cached)");
    }

    // ---- submitVerificationCode ----

    @Test
    void submitVerificationCode_withValidCode_shouldCreateUserAndGenerateToken() {
        userService.requestVerificationCode(TEST_PHONE);
        VerificationCode vc = userRedisService.getVerificationCode(TEST_PHONE);

        User user = userService.submitVerificationCode(TEST_PHONE, vc.getCode());

        assertNotNull(user);
        assertNotNull(user.getId(), "User should have an ID");
        assertEquals(TEST_PHONE, user.getPhone());
        assertNotNull(user.getToken(), "Token should be generated");

        // Verify user persisted in MongoDB
        User dbUser = userRepository.getByPhone(TEST_PHONE);
        assertNotNull(dbUser, "User should exist in MongoDB");
        assertEquals(user.getId(), dbUser.getId());
        assertEquals(user.getToken(), dbUser.getToken());
    }

    @Test
    void submitVerificationCode_withDevCode111_shouldSucceed() {
        userService.requestVerificationCode(TEST_PHONE);

        User user = userService.submitVerificationCode(TEST_PHONE, "111");

        assertNotNull(user);
        assertEquals(TEST_PHONE, user.getPhone());
        assertNotNull(user.getToken());
    }

    @Test
    void submitVerificationCode_withWrongCode_shouldThrowException() {
        userService.requestVerificationCode(TEST_PHONE);

        VideoException ex = assertThrows(VideoException.class, () ->
                userService.submitVerificationCode(TEST_PHONE, "9999"));

        assertEquals(ErrorCode.USER_PHONE_VERIFICATION_CODE_WRONG, ex.getErrorCode());
    }

    @Test
    void submitVerificationCode_withExpiredCode_shouldThrowException() {
        // No verification code requested — simulates expiration
        VideoException ex = assertThrows(VideoException.class, () ->
                userService.submitVerificationCode(TEST_PHONE, "1234"));

        assertEquals(ErrorCode.USER_PHONE_VERIFICATION_CODE_EXPIRED, ex.getErrorCode());
    }

    @Test
    void submitVerificationCode_forExistingUser_shouldUpdateNotDuplicate() {
        // First login
        userService.requestVerificationCode(TEST_PHONE);
        User firstUser = userService.submitVerificationCode(TEST_PHONE, "111");
        String firstToken = firstUser.getToken();
        String userId = firstUser.getId();

        // Second login
        userService.requestVerificationCode(TEST_PHONE);
        User secondUser = userService.submitVerificationCode(TEST_PHONE, "111");

        // Same user ID, different token
        assertEquals(userId, secondUser.getId(), "User ID should remain the same");
        assertNotEquals(firstToken, secondUser.getToken(), "Token should be refreshed");

        // Only one user in DB for this phone
        long count = mongoTemplate.getCollection("user").countDocuments();
        assertEquals(1, count, "Should not create a duplicate user");
    }

    @Test
    void submitVerificationCode_shouldRemoveVerificationCodeFromRedis() {
        userService.requestVerificationCode(TEST_PHONE);
        userService.submitVerificationCode(TEST_PHONE, "111");

        VerificationCode vc = userRedisService.getVerificationCode(TEST_PHONE);
        assertNull(vc, "Verification code should be deleted from Redis after successful submit");
    }

    // ---- getUserByToken ----

    @Test
    void getUserByToken_shouldReturnUserFromRedisCache() {
        userService.requestVerificationCode(TEST_PHONE);
        User created = userService.submitVerificationCode(TEST_PHONE, "111");

        // Token should be cached in Redis after login
        User cached = userService.getUserByToken(created.getToken());
        assertNotNull(cached);
        assertEquals(created.getId(), cached.getId());
        assertEquals(created.getPhone(), cached.getPhone());
    }

    @Test
    void getUserByToken_shouldFallBackToMongoDB() {
        userService.requestVerificationCode(TEST_PHONE);
        User created = userService.submitVerificationCode(TEST_PHONE, "111");

        // Clear only the token key from Redis to force a DB lookup
        cleanRedisKeys(RedisKey.token(created.getToken()));

        User fromDb = userService.getUserByToken(created.getToken());
        assertNotNull(fromDb, "Should fall back to MongoDB when Redis cache is empty");
        assertEquals(created.getId(), fromDb.getId());
    }

    @Test
    void getUserByToken_withInvalidToken_shouldReturnNull() {
        User user = userService.getUserByToken("non-existent-token");
        assertNull(user);
    }

    @Test
    void getUserByToken_withNullToken_shouldReturnNull() {
        User user = userService.getUserByToken(null);
        assertNull(user);
    }

    // ---- getUserById ----

    @Test
    void getUserById_shouldReturnUserWithoutToken() {
        userService.requestVerificationCode(TEST_PHONE);
        User created = userService.submitVerificationCode(TEST_PHONE, "111");

        User found = userService.getUserById(created.getId());

        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals(TEST_PHONE, found.getPhone());
        assertNull(found.getToken(), "Token should be nullified in getUserById response");
    }

    @Test
    void getUserById_withNonExistentId_shouldReturnNull() {
        User user = userService.getUserById("non-existent-id");
        assertNull(user);
    }
}
