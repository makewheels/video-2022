package com.github.makewheels.video2022.transcode;

import com.aliyun.mts20140618.Client;
import com.aliyun.mts20140618.models.*;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AliyunMpsServiceTest {

    @InjectMocks
    private AliyunMpsService aliyunMpsService;

    private Client mockClient;

    @BeforeEach
    void setUp() {
        mockClient = mock(Client.class);
        ReflectionTestUtils.setField(aliyunMpsService, "client", mockClient);
        ReflectionTestUtils.setField(aliyunMpsService, "bucket", "test-video-bucket");
        ReflectionTestUtils.setField(aliyunMpsService, "accessKeyId", "test-ak");
        ReflectionTestUtils.setField(aliyunMpsService, "accessKeySecret", "test-sk");
    }

    // --- getMediaInfo ---

    @Test
    void getMediaInfo_shouldSetInputAndCallClient() throws Exception {
        SubmitMediaInfoJobResponse expectedResponse = new SubmitMediaInfoJobResponse();
        when(mockClient.submitMediaInfoJob(any(SubmitMediaInfoJobRequest.class)))
                .thenReturn(expectedResponse);

        SubmitMediaInfoJobResponse result = aliyunMpsService.getMediaInfo("videos/raw/test.mp4");

        assertSame(expectedResponse, result);

        ArgumentCaptor<SubmitMediaInfoJobRequest> captor =
                ArgumentCaptor.forClass(SubmitMediaInfoJobRequest.class);
        verify(mockClient).submitMediaInfoJob(captor.capture());

        SubmitMediaInfoJobRequest request = captor.getValue();
        assertNotNull(request.getInput());
        assertTrue(request.getInput().contains("test-video-bucket"));
        assertFalse(request.getAsync());
    }

    @Test
    void getMediaInfo_clientThrows_shouldReturnNull() throws Exception {
        when(mockClient.submitMediaInfoJob(any())).thenThrow(new RuntimeException("SDK error"));

        SubmitMediaInfoJobResponse result = aliyunMpsService.getMediaInfo("videos/raw/test.mp4");

        assertNull(result);
    }

    // --- submitTranscodeJobByResolution ---

    @Test
    void submitTranscodeJob_480p_shouldUseCorrectTemplateId() throws Exception {
        SubmitJobsResponse expectedResponse = new SubmitJobsResponse();
        when(mockClient.submitJobs(any(SubmitJobsRequest.class))).thenReturn(expectedResponse);

        SubmitJobsResponse result = aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", Resolution.R_480P);

        assertSame(expectedResponse, result);

        ArgumentCaptor<SubmitJobsRequest> captor = ArgumentCaptor.forClass(SubmitJobsRequest.class);
        verify(mockClient).submitJobs(captor.capture());

        String outputs = captor.getValue().getOutputs();
        assertTrue(outputs.contains("6db7941bf7ec43c4a4ecc7f67d87ace6"));
    }

    @Test
    void submitTranscodeJob_720p_shouldUseCorrectTemplateId() throws Exception {
        SubmitJobsResponse expectedResponse = new SubmitJobsResponse();
        when(mockClient.submitJobs(any(SubmitJobsRequest.class))).thenReturn(expectedResponse);

        aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", Resolution.R_720P);

        ArgumentCaptor<SubmitJobsRequest> captor = ArgumentCaptor.forClass(SubmitJobsRequest.class);
        verify(mockClient).submitJobs(captor.capture());

        assertTrue(captor.getValue().getOutputs().contains("f96c8ccf81c44f079d285e13c1a1a104"));
    }

    @Test
    void submitTranscodeJob_1080p_shouldUseCorrectTemplateId() throws Exception {
        SubmitJobsResponse expectedResponse = new SubmitJobsResponse();
        when(mockClient.submitJobs(any(SubmitJobsRequest.class))).thenReturn(expectedResponse);

        aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", Resolution.R_1080P);

        ArgumentCaptor<SubmitJobsRequest> captor = ArgumentCaptor.forClass(SubmitJobsRequest.class);
        verify(mockClient).submitJobs(captor.capture());

        assertTrue(captor.getValue().getOutputs().contains("438e72fb70d04b89bf2b37b2769cf1ec"));
    }

    @Test
    void submitTranscodeJob_unknownResolution_shouldReturnNull() {
        SubmitJobsResponse result = aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", "360p");

        assertNull(result);
        verifyNoInteractions(mockClient);
    }

    @Test
    void submitTranscodeJob_m3u8Suffix_shouldBeStripped() throws Exception {
        SubmitJobsResponse expectedResponse = new SubmitJobsResponse();
        when(mockClient.submitJobs(any(SubmitJobsRequest.class))).thenReturn(expectedResponse);

        aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", Resolution.R_480P);

        ArgumentCaptor<SubmitJobsRequest> captor = ArgumentCaptor.forClass(SubmitJobsRequest.class);
        verify(mockClient).submitJobs(captor.capture());

        // Output should have .m3u8 stripped
        String outputs = captor.getValue().getOutputs();
        assertFalse(outputs.contains(".m3u8"));
    }

    @Test
    void submitTranscodeJob_shouldSetPipelineAndBucket() throws Exception {
        SubmitJobsResponse expectedResponse = new SubmitJobsResponse();
        when(mockClient.submitJobs(any(SubmitJobsRequest.class))).thenReturn(expectedResponse);

        aliyunMpsService.submitTranscodeJobByResolution(
                "videos/raw/src.mp4", "videos/transcode/out.m3u8", Resolution.R_480P);

        ArgumentCaptor<SubmitJobsRequest> captor = ArgumentCaptor.forClass(SubmitJobsRequest.class);
        verify(mockClient).submitJobs(captor.capture());

        SubmitJobsRequest request = captor.getValue();
        assertEquals("6c126c07a9b34a85b7093e7bfa9c3ad9", request.getPipelineId());
        assertEquals("test-video-bucket", request.getOutputBucket());
        assertEquals("oss-cn-beijing", request.getOutputLocation());
    }

    // --- queryTranscodeJob ---

    @Test
    void queryTranscodeJob_shouldPassJobIds() throws Exception {
        QueryJobListResponse expectedResponse = new QueryJobListResponse();
        when(mockClient.queryJobList(any(QueryJobListRequest.class))).thenReturn(expectedResponse);

        QueryJobListResponse result = aliyunMpsService.queryTranscodeJob("job1,job2");

        assertSame(expectedResponse, result);

        ArgumentCaptor<QueryJobListRequest> captor =
                ArgumentCaptor.forClass(QueryJobListRequest.class);
        verify(mockClient).queryJobList(captor.capture());
        assertEquals("job1,job2", captor.getValue().getJobIds());
    }

    @Test
    void queryTranscodeJob_clientThrows_shouldReturnNull() throws Exception {
        when(mockClient.queryJobList(any())).thenThrow(new RuntimeException("SDK error"));

        assertNull(aliyunMpsService.queryTranscodeJob("bad-job"));
    }

    // --- submitSnapshotJob ---

    @Test
    void submitSnapshotJob_shouldSetSourceAndTarget() throws Exception {
        SubmitSnapshotJobResponse expectedResponse = new SubmitSnapshotJobResponse();
        when(mockClient.submitSnapshotJob(any(SubmitSnapshotJobRequest.class)))
                .thenReturn(expectedResponse);

        SubmitSnapshotJobResponse result = aliyunMpsService.submitSnapshotJob(
                "videos/raw/src.mp4", "videos/snapshot/cover.jpg");

        assertSame(expectedResponse, result);

        ArgumentCaptor<SubmitSnapshotJobRequest> captor =
                ArgumentCaptor.forClass(SubmitSnapshotJobRequest.class);
        verify(mockClient).submitSnapshotJob(captor.capture());

        SubmitSnapshotJobRequest request = captor.getValue();
        assertTrue(request.getInput().contains("test-video-bucket"));
        assertTrue(request.getSnapshotConfig().contains("cover.jpg"));
        assertEquals("158c291025294f05b7d012a070ac8c28", request.getPipelineId());
    }
}
