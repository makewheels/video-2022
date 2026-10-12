package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.transcode.TranscodeLauncher;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionClient;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CoverTaskSubmissionTest extends BaseIntegrationTest {
    @Autowired private CoverLauncher coverLauncher;
    @Autowired private TranscodeLauncher transcodeLauncher;
    @MockitoSpyBean private CloudFunctionClient cloudFunctionClient;

    @Test
    void selfHostedUpload_submitsCoverWithDurablePayloadAndAttempt() {
        cleanDatabase();
        ReflectionTestUtils.setField(transcodeLauncher, "pipeline", "self-hosted");
        try {
            User user = new User();
            user.setId("u_cover_submission");
            File raw = new File();
            raw.setId("f_cover_raw");
            raw.setKey("videos/u_cover_submission/202610/v_cover_submission/raw/source.mp4");
            mongoTemplate.save(raw);
            Video video = new Video();
            video.setId("v_cover_submission");
            video.setUploaderId(user.getId());
            video.setCreateTime(new Date());
            video.setVideoType(VideoType.USER_UPLOAD);
            video.setProvider(ObjectStorageProvider.ALIYUN_OSS);
            video.setRawFileId(raw.getId());
            mongoTemplate.save(video);
            doAnswer(invocation -> {
                JSONObject payload = invocation.getArgument(1);
                FcTask stored = mongoTemplate.findById(payload.getString("taskId"), FcTask.class);
                assertNotNull(stored);
                assertEquals(FcTaskStatus.SUBMITTED, stored.getStatus());
                assertEquals(stored.getAttemptId(), payload.getString("attemptId"));
                assertEquals(payload, stored.getPayloadSnapshot());
                assertTrue(payload.getString("imageKey").startsWith(
                        stored.getOutputBaseDir() + "/attempts/" + stored.getAttemptId() + "/"));
                CloudFunctionClient.SubmitResult receipt = new CloudFunctionClient.SubmitResult();
                receipt.setAccepted(true);
                receipt.setRequestId("cover-request");
                receipt.setHttpStatus(202);
                return receipt;
            }).when(cloudFunctionClient).submit(anyString(), any(JSONObject.class));
            coverLauncher.createCover(user, video);
            verify(cloudFunctionClient, times(1)).submit(anyString(), any(JSONObject.class));
            Video saved = mongoTemplate.findById(video.getId(), Video.class);
            Cover cover = mongoTemplate.findById(saved.getCoverId(), Cover.class);
            FcTask task = mongoTemplate.findById(cover.getJobId(), FcTask.class);
            assertEquals("cover-request", task.getFcRequestId());
            assertEquals(1, task.getAttemptCount());
            assertEquals(300_000L, task.getExecutionTimeoutMs());
        } finally {
            ReflectionTestUtils.setField(transcodeLauncher, "pipeline", "legacy");
            cleanDatabase();
        }
    }
}
