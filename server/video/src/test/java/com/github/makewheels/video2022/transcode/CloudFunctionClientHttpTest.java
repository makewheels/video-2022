package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real loopback transport, without Spring, MongoDB, FC requests or paid execution. */
class CloudFunctionClientHttpTest {
    private HttpServer server;
    private CloudFunctionClient client;
    private final AtomicReference<Captured> captured = new AtomicReference<>();
    private final AtomicInteger received = new AtomicInteger();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile int status;
    private volatile boolean disconnect;

    private record Captured(String path, String method, String invocation, String secret, JSONObject body) { }

    @BeforeEach
    void start() throws Exception {
        status = 202;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                received.incrementAndGet();
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                captured.set(new Captured(exchange.getRequestURI().getPath(), exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("X-Fc-Invocation-Type"),
                        exchange.getRequestHeaders().getFirst("X-Invoke-Secret"), JSONObject.parseObject(body)));
                if (!disconnect) {
                    exchange.getResponseHeaders().add("X-Fc-Request-Id", "fixture-request-id");
                    exchange.getResponseHeaders().add("Location", "/gpu");
                    exchange.sendResponseHeaders(status, -1);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = new CloudFunctionClient();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(client, "cpuFunctionUrl", base + "/cpu");
        ReflectionTestUtils.setField(client, "gpuFunctionUrl", base + "/gpu");
        ReflectionTestUtils.setField(client, "invokeSecret", "fixture-invoke-secret");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private JSONObject payload() {
        JSONObject result = new JSONObject();
        result.put("schemaVersion", 1);
        result.put("taskId", "fixture-task");
        result.put("attemptId", "fixture-attempt");
        result.put("inputKey", "fixture/测试.mp4");
        return result;
    }

    @Test
    void acceptedCpuRequestPreservesAsyncHeadersJsonAndRequestId() {
        JSONObject payload = payload();
        CloudFunctionClient.SubmitResult result = client.submit(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU, payload);
        assertTrue(result.isAccepted());
        assertFalse(result.isThrottled());
        assertEquals(202, result.getHttpStatus());
        assertEquals("application/json;charset=UTF-8", contentType.get());
        assertEquals(1, received.get());
        assertEquals("fixture-request-id", result.getRequestId());
        assertEquals(new Captured("/cpu", "POST", "Async", "fixture-invoke-secret", payload), captured.get());
    }

    @Test
    void gpuUsesSeparateConfiguredEndpoint() {
        assertTrue(client.submit(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU, payload()).isAccepted());
        assertEquals("/gpu", captured.get().path());
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308, 400, 403, 429, 503})
    void actualHttpErrorsKeepStatusAndOnly429IsThrottled(int responseStatus) {
        status = responseStatus;
        CloudFunctionClient.SubmitResult result = client.submit(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU, payload());
        assertFalse(result.isAccepted());
        assertEquals(responseStatus == 429, result.isThrottled());
        assertEquals(responseStatus, result.getHttpStatus());
        assertEquals(1, received.get());
        assertEquals("fixture-request-id", result.getRequestId());
        assertEquals("fixture-attempt", captured.get().body().getString("attemptId"));
    }

    @Test
    void disconnectAfterBodyKeepsAcceptanceUnknownRatherThanThrottle() {
        disconnect = true;
        CloudFunctionClient.SubmitResult result = client.submit(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU, payload());
        assertNotNull(captured.get(), "Request reached the peer before connection loss");
        assertEquals(1, received.get(), "An uncertain POST must not be resubmitted by the transport");
        assertFalse(result.isAccepted());
        assertFalse(result.isThrottled());
        assertEquals(0, result.getHttpStatus());
        assertNull(result.getRequestId());
    }
}
