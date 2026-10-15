package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskCallbackService;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.transcode.task.FcTaskSubmitter;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Mongo state + hand-built objects; no application scheduling or paid requests. */
class FcSubmissionPersistenceTest {
    private MongoClient mongo;
    private MongoTemplate template;
    private String database;
    private CloudFunctionClient client;
    private FcTaskCallbackService callback;

    @BeforeEach
    void start() {
        database = "fc_submit_" + UUID.randomUUID().toString().replace("-", "");
        int port = Integer.getInteger("spring.mongodb.port", 27017);
        mongo = MongoClients.create("mongodb://127.0.0.1:" + port
                + "/?serverSelectionTimeoutMS=3000&connectTimeoutMS=3000&socketTimeoutMS=3000");
        template = new MongoTemplate(mongo, database);
        Document hello = template.getDb().runCommand(new Document("hello", 1));
        assertFalse(hello.containsKey("setName"), "Fixtures require isolated standalone Mongo");
        assertNotEquals("isdbgrid", hello.getString("msg"));
        client = mock(CloudFunctionClient.class);
        callback = mock(FcTaskCallbackService.class);
    }

    @AfterEach
    void clean() {
        try {
            if (mongo != null && database != null && database.matches("fc_submit_[a-f0-9]{32}")) {
                mongo.getDatabase(database).drop();
            }
        } finally {
            if (mongo != null) mongo.close();
        }
    }

    private FcTaskRepository repository(MongoTemplate target) {
        FcTaskRepository repo = new FcTaskRepository();
        ReflectionTestUtils.setField(repo, "mongoTemplate", target);
        return repo;
    }

    private FcTaskSubmitter submitter(FcTaskRepository repo, MongoTemplate target) {
        FcTaskSubmitter result = new FcTaskSubmitter();
        ReflectionTestUtils.setField(result, "fcTaskRepository", repo);
        ReflectionTestUtils.setField(result, "mongoTemplate", target);
        ReflectionTestUtils.setField(result, "cloudFunctionClient", client);
        ReflectionTestUtils.setField(result, "callbackService", callback);
        return result;
    }

    private FcTask task(FcTaskSubmitter submitter) {
        FcTask result = submitter.createTask("PROBE", "fixture-video", null);
        result.setAttemptId("fixture-attempt");
        result.setAttemptCount(1);
        result.setProvider(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU);
        result.setExecutionTimeoutMs(300_000L);
        return result;
    }

    private JSONObject payload() {
        JSONObject result = new JSONObject();
        result.put("schemaVersion", 1);
        result.put("operation", "PROBE");
        result.put("taskId", "fc-PROBE-fixture-video");
        result.put("attemptId", "fixture-attempt");
        result.put("videoId", "fixture-video");
        result.put("inputKey", "videos/fixture/source.mp4");
        result.put("outputDir", "videos/fixture/probe");
        return result;
    }

    private void assertSnapshot(FcTask durable) {
        assertNotNull(durable);
        assertEquals("fixture-attempt", durable.getAttemptId());
        assertEquals(1, durable.getAttemptCount());
        assertTrue(durable.getDeadline().after(new Date()));
        assertEquals("videos/fixture/source.mp4", durable.getPayloadSnapshot().getString("inputKey"));
        assertEquals("videos/fixture/probe/attempts/fixture-attempt",
                durable.getPayloadSnapshot().getString("outputDir"));
    }

    @Test
    void interruptedAfterDispatchRebuiltObjectsDoNotDispatchAgain() {
        FcTaskRepository repo = repository(template);
        FcTaskSubmitter initial = submitter(repo, template);
        FcTask original = task(initial);
        RuntimeException interrupted = new RuntimeException("fixture-after-dispatch");
        when(client.submit(eq(original.getProvider()), any(JSONObject.class))).thenAnswer(call -> {
            FcTask durable = repo.getById(original.getId());
            assertSnapshot(durable);
            assertEquals(FcTaskStatus.SUBMITTED, durable.getStatus());
            JSONObject sent = call.getArgument(1);
            assertEquals(sent, durable.getPayloadSnapshot());
            throw interrupted;
        });
        assertSame(interrupted, assertThrows(RuntimeException.class, () -> initial.submit(original, payload())));
        Document durable = template.getCollection("fcTask").find().first();
        assertNotNull(durable);
        assertFalse(durable.containsKey("fcRequestId"));
        MongoTemplate fresh = new MongoTemplate(mongo, database);
        FcTaskRepository rebuiltRepo = repository(fresh);
        FcTaskSubmitter rebuilt = submitter(rebuiltRepo, fresh);
        FcTask reloaded = rebuiltRepo.getById(original.getId());
        assertFalse(rebuilt.submit(reloaded, reloaded.getPayloadSnapshot()));
        verify(client, times(1)).submit(eq(original.getProvider()), any(JSONObject.class));
        verifyNoInteractions(callback);
        assertEquals(durable, fresh.getCollection("fcTask").find().first());
        assertEquals(1, fresh.getCollection("fcTask").countDocuments());
    }

    @Test
    void interruptedAfterPersistBeforeClaimCanSafelySubmitSameAttempt() {
        RuntimeException interrupted = new RuntimeException("fixture-before-claim");
        FcTaskRepository interruptedRepo = new FcTaskRepository() {
            @Override
            public boolean updateCurrent(FcTask current, String status, Update update) {
                assertSnapshot(getById(current.getId()));
                assertEquals(FcTaskStatus.CREATED, getById(current.getId()).getStatus());
                throw interrupted;
            }
        };
        ReflectionTestUtils.setField(interruptedRepo, "mongoTemplate", template);
        FcTaskSubmitter initial = submitter(interruptedRepo, template);
        FcTask original = task(initial);
        assertSame(interrupted, assertThrows(RuntimeException.class, () -> initial.submit(original, payload())));
        verifyNoInteractions(client, callback);
        MongoTemplate fresh = new MongoTemplate(mongo, database);
        FcTaskRepository rebuiltRepo = repository(fresh);
        FcTask reloaded = rebuiltRepo.getById(original.getId());
        assertSnapshot(reloaded);
        assertEquals(FcTaskStatus.CREATED, reloaded.getStatus());
        CloudFunctionClient.SubmitResult accepted = new CloudFunctionClient.SubmitResult();
        accepted.setAccepted(true);
        accepted.setHttpStatus(202);
        accepted.setRequestId("fixture-request-id");
        when(client.submit(eq(original.getProvider()), any(JSONObject.class))).thenReturn(accepted);
        assertTrue(submitter(rebuiltRepo, fresh).submit(reloaded, reloaded.getPayloadSnapshot()));
        FcTask saved = rebuiltRepo.getById(original.getId());
        assertSnapshot(saved);
        assertEquals(FcTaskStatus.SUBMITTED, saved.getStatus());
        assertEquals("fixture-request-id", saved.getFcRequestId());
        verify(client, times(1)).submit(eq(original.getProvider()), any(JSONObject.class));
        verifyNoInteractions(callback);
        assertEquals(1, fresh.getCollection("fcTask").countDocuments());
    }
}
