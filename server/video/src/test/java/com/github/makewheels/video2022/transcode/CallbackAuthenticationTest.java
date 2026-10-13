package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.cover.CoverCallbackService;
import com.github.makewheels.video2022.cover.CoverController;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CallbackAuthenticationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingConfiguredSecret_cannotAuthorizeEvenMatchingHeader(String secret) {
        TranscodeController transcode = new TranscodeController();
        ReflectionTestUtils.setField(transcode, "callbackSecret", secret);
        assertEquals(HttpStatus.FORBIDDEN,
                transcode.aliyunCloudFunctionTranscodeCallback(secret, new JSONObject()).getStatusCode());
        CoverController cover = new CoverController();
        ReflectionTestUtils.setField(cover, "callbackSecret", secret);
        assertEquals(HttpStatus.FORBIDDEN, cover.youtubeUploadFinishCallback(secret, "c_test").getStatusCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong"})
    void missingOrWrongHeader_rejectedBeforeDispatch(String secret) {
        TranscodeController transcode = new TranscodeController();
        ReflectionTestUtils.setField(transcode, "callbackSecret", "configured-test-secret");
        assertEquals(HttpStatus.FORBIDDEN,
                transcode.aliyunCloudFunctionTranscodeCallback(secret, new JSONObject()).getStatusCode());
        CoverController cover = new CoverController();
        ReflectionTestUtils.setField(cover, "callbackSecret", "configured-test-secret");
        assertEquals(HttpStatus.FORBIDDEN, cover.youtubeUploadFinishCallback(secret, "c_test").getStatusCode());
    }

    @Test void configuredSecret_preservesAuthorizedDispatch() {
        TranscodeController transcode = new TranscodeController();
        TranscodeFactory factory = mock(TranscodeFactory.class);
        TranscodeService service = mock(TranscodeService.class);
        ReflectionTestUtils.setField(transcode, "callbackSecret", "configured-test-secret");
        ReflectionTestUtils.setField(transcode, "transcodeFactory", factory);
        when(factory.getService(TranscodeProvider.ALIYUN_CLOUD_FUNCTION)).thenReturn(service);
        JSONObject body = new JSONObject();
        body.put("jobId", "existing-job");
        assertEquals(HttpStatus.OK,
                transcode.aliyunCloudFunctionTranscodeCallback("configured-test-secret", body).getStatusCode());
        verify(service).callback("existing-job");
        CoverController cover = new CoverController();
        CoverCallbackService coverService = mock(CoverCallbackService.class);
        ReflectionTestUtils.setField(cover, "callbackSecret", "configured-test-secret");
        ReflectionTestUtils.setField(cover, "coverCallbackService", coverService);
        when(coverService.youtubeUploadFinishCallback("c_test")).thenReturn(Result.ok());
        assertEquals(HttpStatus.OK,
                cover.youtubeUploadFinishCallback("configured-test-secret", "c_test").getStatusCode());
        verify(coverService).youtubeUploadFinishCallback("c_test");
    }
}
