package com.github.makewheels.video2022.user;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** Current Java converter behavior over raw, synthetic BSON, not a proposed Python mapping. */
class UserMongoReadContractTest extends BaseIntegrationTest {
    @Autowired
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        assertEquals("user", mongoTemplate.getCollectionName(User.class));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void missingFieldsRetainDefaultsButExplicitNullFieldsClearThem() {
        for (boolean explicitNull : new boolean[]{false, true}) {
            String id = "fixture-defaults-" + explicitNull;
            Document raw = new Document("_id", id).append("token", "fixture-token-" + explicitNull);
            if (explicitNull) {
                raw.append("createTime", null).append("updateTime", null)
                        .append("subscriberCount", null).append("videoCount", null);
            }
            mongoTemplate.getCollection("user").insertOne(raw);
            long start = System.currentTimeMillis();
            User user = repository.getByToken("fixture-token-" + explicitNull);
            long end = System.currentTimeMillis();
            assertNotNull(user);
            if (explicitNull) {
                assertNull(user.getSubscriberCount());
                assertNull(user.getVideoCount());
                assertNull(user.getCreateTime());
                assertNull(user.getUpdateTime());
                continue;
            }
            assertEquals(0L, user.getSubscriberCount());
            assertEquals(0L, user.getVideoCount());
            assertNotNull(user.getCreateTime());
            assertNotNull(user.getUpdateTime());
            assertTrue(user.getCreateTime().getTime() >= start && user.getCreateTime().getTime() <= end);
            assertTrue(user.getUpdateTime().getTime() >= start && user.getUpdateTime().getTime() <= end);
            assertNull(user.getPhone());
        }
    }

    @Test
    void hexadecimalIdLookupChoosesObjectIdWhileTokenCanReadStringId() {
        String hex = "0123456789abcdef01234567";
        mongoTemplate.getCollection("user").insertOne(new Document("_id", hex).append("token", "fixture-string-id"));
        mongoTemplate.getCollection("user").insertOne(
                new Document("_id", new ObjectId(hex)).append("token", "fixture-object-id"));
        User byId = repository.getById(hex);
        assertNotNull(byId);
        assertEquals(hex, byId.getId());
        assertEquals("fixture-object-id", byId.getToken());
        User stringId = repository.getByToken("fixture-string-id");
        assertNotNull(stringId);
        assertEquals(hex, stringId.getId());
        mongoTemplate.getCollection("user").deleteOne(new Document("_id", new ObjectId(hex)));
        assertNull(repository.getById(hex));
        assertNotNull(repository.getByToken("fixture-string-id"));
    }

    @Test
    void commonNumericValuesAreConvertedAndTokenQueryRemainsTypeSensitive() {
        Date fixed = new Date(1704164645000L);
        mongoTemplate.getCollection("user").insertOne(new Document("_id", "fixture-numbers")
                .append("token", "fixture-numeric-fields").append("phone", 0L)
                .append("subscriberCount", 12.75).append("videoCount", "7")
                .append("createTime", fixed).append("updateTime", fixed));
        User user = repository.getByToken("fixture-numeric-fields");
        assertEquals("0", user.getPhone());
        assertEquals(12L, user.getSubscriberCount());
        assertEquals(7L, user.getVideoCount());
        assertEquals(fixed, user.getCreateTime());
        mongoTemplate.getCollection("user").insertOne(
                new Document("_id", "fixture-numeric-token").append("token", 1234L));
        assertNull(repository.getByToken("1234"));
        assertEquals("1234", repository.getById("fixture-numeric-token").getToken());
    }

    @Test
    void fileIdsUseObjectIdWithoutFallbackAndDoNotFilterTypeOwnerOrDeletedFlag() {
        assertEquals("file", mongoTemplate.getCollectionName(File.class));
        String hex = "0123456789abcdef01234567";
        mongoTemplate.getCollection("file").insertOne(new Document("_id", hex).append("key", "fixture-string-key"));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", new ObjectId(hex))
                .append("key", "fixture-object-key").append("fileType", "RAW_VIDEO")
                .append("uploaderId", "fixture-other-owner").append("deleted", true));
        File file = mongoTemplate.findById(hex, File.class);
        assertNotNull(file);
        assertEquals("fixture-object-key", file.getKey());
        assertEquals("RAW_VIDEO", file.getFileType());
        assertTrue(file.getDeleted());
        mongoTemplate.getCollection("file").deleteOne(new Document("_id", new ObjectId(hex)));
        assertNull(mongoTemplate.findById(hex, File.class));
    }

    @Test
    void fileKeyRetainsNullEmptyWhitespaceAndConvertsIntegralBson() {
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-missing-key"));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-null-key").append("key", null));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-empty-key").append("key", ""));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-space-key").append("key", " "));
        mongoTemplate.getCollection("file").insertOne(new Document("_id", "fixture-numeric-key").append("key", 1234L));
        assertNull(mongoTemplate.findById("fixture-missing-key", File.class).getKey());
        assertNull(mongoTemplate.findById("fixture-null-key", File.class).getKey());
        assertEquals("", mongoTemplate.findById("fixture-empty-key", File.class).getKey());
        assertEquals(" ", mongoTemplate.findById("fixture-space-key", File.class).getKey());
        assertEquals("1234", mongoTemplate.findById("fixture-numeric-key", File.class).getKey());
    }

    @Test
    void profileUpdateUsesObjectIdWithoutUpsertOrStringFallbackAndWritesExplicitNull() {
        String hex = "0123456789abcdef01234567";
        mongoTemplate.getCollection("user").insertOne(new Document("_id", hex).append("nickname", "fixture-string"));
        mongoTemplate.getCollection("user").insertOne(new Document("_id", new ObjectId(hex))
                .append("nickname", "fixture-object").append("bio", "fixture-old")
                .append("token", "fixture-retained-token"));
        repository.updateProfile(hex, null, " ");
        Document updated = mongoTemplate.getCollection("user").find(new Document("_id", new ObjectId(hex))).first();
        assertNotNull(updated);
        assertTrue(updated.containsKey("nickname"));
        assertNull(updated.get("nickname"));
        assertEquals(" ", updated.getString("bio"));
        assertNotNull(updated.getDate("updateTime"));
        assertEquals("fixture-retained-token", updated.getString("token"));
        mongoTemplate.getCollection("user").deleteOne(new Document("_id", new ObjectId(hex)));
        repository.updateProfile(hex, "fixture-no-match", null);
        repository.updateProfile("fixture-missing", "fixture-no-upsert", null);
        assertEquals(1, mongoTemplate.getCollection("user").countDocuments());
        assertEquals("fixture-string", mongoTemplate.getCollection("user").find().first().getString("nickname"));
    }
}
