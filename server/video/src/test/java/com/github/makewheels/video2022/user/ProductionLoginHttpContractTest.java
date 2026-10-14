package com.github.makewheels.video2022.user;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.user.bean.VerificationCode;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Property binding is real; the database and external services remain isolated test fixtures. */
@TestPropertySource(properties = {
        "spring.profiles.active=prod",
        "spring.mongodb.database=video-2022-test",
        "spring.mongodb.uri=mongodb://127.0.0.1:${spring.mongodb.port:27017}/video-2022-test"
})
class ProductionLoginHttpContractTest extends BaseIntegrationTest {
    private static final String PHONE = "13800000000";

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private boolean isolatedDatabase;

    @BeforeEach
    void setUp() {
        assertEquals("video-2022-test", mongoTemplate.getDb().getName());
        isolatedDatabase = true;
        cleanDatabase();
        assertEquals("prod", environmentService.getEnvironment());
        assertTrue(environmentService.isProductionEnv());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        if (isolatedDatabase) {
            cleanDatabase();
        }
    }

    private VerificationCode seedCode() {
        VerificationCode code = new VerificationCode();
        code.setPhone(PHONE);
        code.setCode("1234");
        code.setCreatedAt(new Date());
        mongoTemplate.save(code);
        return code;
    }

    private JSONObject submit(String code) throws Exception {
        MvcResult result = mvc.perform(get("/user/submitVerificationCode")
                .param("phone", PHONE).param("code", code)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith("application/json"));
        return JSON.parseObject(result.getResponse().getContentAsString());
    }

    @Test
    void productionPropertyRejects111WithoutConsumingStoredCode() throws Exception {
        VerificationCode stored = seedCode();
        JSONObject rejected = submit("111");
        assertEquals(11, rejected.getIntValue("code"));
        assertEquals("验证码错误", rejected.getString("message"));
        assertNull(rejected.get("data"));
        assertNotNull(mongoTemplate.findById(stored.getId(), VerificationCode.class));
        assertEquals(0, mongoTemplate.getCollection("user").countDocuments());
    }

    @Test
    void productionPropertyAcceptsCorrectCodeAndRemovesIt() throws Exception {
        seedCode();
        JSONObject accepted = submit("1234");
        assertEquals(0, accepted.getIntValue("code"));
        String id = accepted.getJSONObject("data").getString("id");
        assertNotNull(mongoTemplate.findById(id, User.class));
        assertNotNull(accepted.getJSONObject("data").getString("token"));
        assertEquals(0, mongoTemplate.getCollection("verification_codes").countDocuments());
        assertEquals(12, submit("1234").getIntValue("code"));
    }

    @Test
    void actualAnnotationIndexesRoundTripWith600SecondTtlAndUniquePhone() {
        assertEquals("verification_codes", mongoTemplate.getCollectionName(VerificationCode.class));
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(
                mongoTemplate.getConverter().getMappingContext());
        for (var definition : resolver.resolveIndexFor(VerificationCode.class)) {
            mongoTemplate.indexOps(VerificationCode.class).createIndex(definition);
        }
        boolean foundTtl = false;
        boolean foundPhone = false;
        for (Document index : mongoTemplate.getCollection("verification_codes").listIndexes()) {
            Document keys = index.get("key", Document.class);
            if (keys.containsKey("createdAt")) {
                assertEquals(600L, ((Number) index.get("expireAfterSeconds")).longValue());
                foundTtl = true;
            }
            if (keys.containsKey("phone")) {
                assertEquals(Boolean.TRUE, index.getBoolean("unique"));
                foundPhone = true;
            }
        }
        assertTrue(foundTtl);
        assertTrue(foundPhone);
    }
}
