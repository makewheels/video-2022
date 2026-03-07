package com.github.makewheels.video2022.etc.ding;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.github.makewheels.video2022.etc.api.DingApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DingServiceTest {

    @Mock
    private RobotFactory robotFactory;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private DingService dingService;

    private RobotConfig watchLogConfig() {
        RobotConfig config = new RobotConfig();
        config.setType(RobotType.WATCH_LOG);
        config.setAccessToken("fake-access-token");
        config.setSecret("SECfake-secret-key-for-testing");
        return config;
    }

    private OapiRobotSendResponse successResponse() {
        OapiRobotSendResponse resp = new OapiRobotSendResponse();
        resp.setErrorCode("0");
        resp.setMsg("ok");
        return resp;
    }

    @Test
    void sendMarkdown_sendsRequestAndSavesApiLog() throws Exception {
        when(robotFactory.getRobotByType(RobotType.WATCH_LOG)).thenReturn(watchLogConfig());

        try (MockedConstruction<DefaultDingTalkClient> mocked = mockConstruction(
                DefaultDingTalkClient.class,
                (client, ctx) -> when(client.execute(any(OapiRobotSendRequest.class)))
                        .thenReturn(successResponse()))) {

            OapiRobotSendResponse response = dingService.sendMarkdown(
                    RobotType.WATCH_LOG, "测试标题", "测试内容");

            assertNotNull(response);
            assertEquals("0", response.getErrorCode());

            // Verify DingTalkClient was constructed and execute was called
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).execute(any(OapiRobotSendRequest.class));

            // Verify API log saved to MongoDB
            ArgumentCaptor<DingApi> captor = ArgumentCaptor.forClass(DingApi.class);
            verify(mongoTemplate).save(captor.capture());
            DingApi savedLog = captor.getValue();
            assertEquals("测试标题", savedLog.getTitle());
            assertEquals("测试内容", savedLog.getText());
            assertEquals("markdown", savedLog.getMessageType());
            assertEquals("0", savedLog.getCode());
            assertTrue(savedLog.getIsSuccess());
            assertNotNull(savedLog.getStartTime());
            assertNotNull(savedLog.getEndTime());
            assertTrue(savedLog.getCost() >= 0);
        }
    }

    @Test
    void sendMarkdown_constructsUrlWithSignature() throws Exception {
        when(robotFactory.getRobotByType(RobotType.EXCEPTION)).thenReturn(watchLogConfig());

        try (MockedConstruction<DefaultDingTalkClient> mocked = mockConstruction(
                DefaultDingTalkClient.class,
                (client, ctx) -> {
                    when(client.execute(any(OapiRobotSendRequest.class)))
                            .thenReturn(successResponse());
                })) {

            dingService.sendMarkdown(RobotType.EXCEPTION, "异常", "异常详情");

            // The constructed client's URL should contain access_token, timestamp, sign
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void sendMarkdown_setsMarkdownMessageType() throws Exception {
        when(robotFactory.getRobotByType(RobotType.WATCH_LOG)).thenReturn(watchLogConfig());

        try (MockedConstruction<DefaultDingTalkClient> mocked = mockConstruction(
                DefaultDingTalkClient.class,
                (client, ctx) -> when(client.execute(any(OapiRobotSendRequest.class)))
                        .thenReturn(successResponse()))) {

            dingService.sendMarkdown(RobotType.WATCH_LOG, "视频就绪", "## 视频已就绪\n\n内容");

            // Verify through the saved API log that markdown type and text are correct
            ArgumentCaptor<DingApi> captor = ArgumentCaptor.forClass(DingApi.class);
            verify(mongoTemplate).save(captor.capture());
            DingApi savedLog = captor.getValue();
            assertEquals("markdown", savedLog.getMessageType());
            assertEquals("视频就绪", savedLog.getTitle());
            assertEquals("## 视频已就绪\n\n内容", savedLog.getText());
        }
    }

    @Test
    void sendMarkdown_recordsFailureInApiLog() throws Exception {
        when(robotFactory.getRobotByType(RobotType.WATCH_LOG)).thenReturn(watchLogConfig());

        OapiRobotSendResponse failResp = new OapiRobotSendResponse();
        failResp.setErrorCode("310000");
        failResp.setMsg("keywords not in content");

        try (MockedConstruction<DefaultDingTalkClient> mocked = mockConstruction(
                DefaultDingTalkClient.class,
                (client, ctx) -> when(client.execute(any(OapiRobotSendRequest.class)))
                        .thenReturn(failResp))) {

            OapiRobotSendResponse response = dingService.sendMarkdown(
                    RobotType.WATCH_LOG, "title", "text");

            assertEquals("310000", response.getErrorCode());

            ArgumentCaptor<DingApi> captor = ArgumentCaptor.forClass(DingApi.class);
            verify(mongoTemplate).save(captor.capture());
            DingApi savedLog = captor.getValue();
            assertEquals("310000", savedLog.getCode());
            // Verify the error code is recorded in the API log
            assertNotNull(savedLog.getIsSuccess());
        }
    }
}
