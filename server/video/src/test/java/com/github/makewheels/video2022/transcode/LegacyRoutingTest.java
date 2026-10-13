package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponse;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyRoutingTest extends BaseIntegrationTest {
    @Autowired private TranscodeLauncher launcher;
    @MockitoBean private FileService fileService;

    @BeforeEach void setup() { cleanDatabase(); }
    @AfterEach void cleanup() { cleanDatabase(); }

    @Test void noApplicableLane_terminatesInsteadOfWaitingForever() {
        Video video = new Video();
        video.setId("v_legacy_low");
        video.setRawFileId("f_legacy_low");
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);
        when(fileService.getKeyByFileId(video.getRawFileId())).thenReturn("videos/raw/source.mp4");
        String response = "{\"body\":{\"mediaInfoJob\":{\"jobId\":\"probe\",\"properties\":{"
                + "\"width\":\"854\",\"height\":\"480\",\"duration\":\"3\",\"bitrate\":\"800\","
                + "\"streams\":{\"videoStreamList\":{\"videoStream\":[{\"codecName\":\"h264\"}]},"
                + "\"audioStreamList\":{\"audioStream\":[]}}}}}}";
        when(aliyunMpsService.getMediaInfo(anyString()))
                .thenReturn(JSONObject.parseObject(response, SubmitMediaInfoJobResponse.class));
        ReflectionTestUtils.invokeMethod(launcher, "legacyTranscode", null, video);
        assertEquals(VideoStatus.TRANSCODE_FAILED, mongoTemplate.findById(video.getId(), Video.class).getStatus());
        assertEquals(0, mongoTemplate.getCollection("transcode").countDocuments());
        verify(aliyunMpsService, never()).submitTranscodeJobByResolution(anyString(), anyString(), anyString());
    }
}
