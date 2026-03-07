package com.github.makewheels.video2022.oss;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.github.makewheels.video2022.oss.service.BaseOssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OssServiceTest {

    @InjectMocks
    private BaseOssService ossService;

    private OSS mockOssClient;

    @BeforeEach
    void setUp() {
        mockOssClient = mock(OSS.class);
        ReflectionTestUtils.setField(ossService, "ossClient", mockOssClient);
        ReflectionTestUtils.setField(ossService, "bucket", "test-video-bucket");
        ReflectionTestUtils.setField(ossService, "endpoint", "oss-cn-beijing.aliyuncs.com");
        ReflectionTestUtils.setField(ossService, "accessKeyId", "test-ak");
        ReflectionTestUtils.setField(ossService, "secretKey", "test-sk");
    }

    // --- doesObjectExist ---

    @Test
    void doesObjectExist_exists_shouldReturnTrue() {
        when(mockOssClient.doesObjectExist("test-video-bucket", "videos/raw/test.mp4"))
                .thenReturn(true);

        assertTrue(ossService.doesObjectExist("videos/raw/test.mp4"));
        verify(mockOssClient).doesObjectExist("test-video-bucket", "videos/raw/test.mp4");
    }

    @Test
    void doesObjectExist_notExists_shouldReturnFalse() {
        when(mockOssClient.doesObjectExist("test-video-bucket", "nonexistent.mp4"))
                .thenReturn(false);

        assertFalse(ossService.doesObjectExist("nonexistent.mp4"));
    }

    // --- getObject ---

    @Test
    void getObject_shouldDelegateToBucketAndKey() {
        OSSObject expected = new OSSObject();
        when(mockOssClient.getObject("test-video-bucket", "videos/raw/test.mp4"))
                .thenReturn(expected);

        OSSObject result = ossService.getObject("videos/raw/test.mp4");

        assertSame(expected, result);
        verify(mockOssClient).getObject("test-video-bucket", "videos/raw/test.mp4");
    }

    // --- generatePresignedUrl ---

    @Test
    void generatePresignedUrl_shouldReturnUrlString() throws MalformedURLException {
        URL fakeUrl = new URL("https://test-video-bucket.oss-cn-beijing.aliyuncs.com/test.mp4?sign=abc");
        when(mockOssClient.generatePresignedUrl(
                eq("test-video-bucket"), eq("test.mp4"), any(Date.class), eq(HttpMethod.GET)
        )).thenReturn(fakeUrl);

        String result = ossService.generatePresignedUrl("test.mp4", Duration.ofHours(1));

        assertNotNull(result);
        assertTrue(result.contains("test-video-bucket"));
        assertTrue(result.contains("test.mp4"));
    }

    @Test
    void generatePresignedUrl_shouldSetExpirationInFuture() throws MalformedURLException {
        URL fakeUrl = new URL("https://example.com/file");
        when(mockOssClient.generatePresignedUrl(
                anyString(), anyString(), any(Date.class), any(HttpMethod.class)
        )).thenReturn(fakeUrl);

        long before = System.currentTimeMillis();
        ossService.generatePresignedUrl("key", Duration.ofHours(2));
        long after = System.currentTimeMillis();

        ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
        verify(mockOssClient).generatePresignedUrl(
                anyString(), anyString(), dateCaptor.capture(), any(HttpMethod.class));

        Date expiration = dateCaptor.getValue();
        long twoHoursMs = Duration.ofHours(2).toMillis();
        assertTrue(expiration.getTime() >= before + twoHoursMs);
        assertTrue(expiration.getTime() <= after + twoHoursMs);
    }

    // --- listAllObjects with pagination ---

    @Test
    void listAllObjects_singlePage_shouldReturnAllObjects() {
        OSSObjectSummary obj1 = new OSSObjectSummary();
        obj1.setKey("videos/001/file1.ts");
        OSSObjectSummary obj2 = new OSSObjectSummary();
        obj2.setKey("videos/001/file2.ts");

        ListObjectsV2Result result = mock(ListObjectsV2Result.class);
        when(result.getObjectSummaries()).thenReturn(Arrays.asList(obj1, obj2));
        when(result.isTruncated()).thenReturn(false);

        when(mockOssClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

        List<OSSObjectSummary> objects = ossService.listAllObjects("videos/001/");

        assertEquals(2, objects.size());
        assertEquals("videos/001/file1.ts", objects.get(0).getKey());
        verify(mockOssClient, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void listAllObjects_multiplePages_shouldPaginateUntilDone() {
        OSSObjectSummary obj1 = new OSSObjectSummary();
        obj1.setKey("file1.ts");
        OSSObjectSummary obj2 = new OSSObjectSummary();
        obj2.setKey("file2.ts");
        OSSObjectSummary obj3 = new OSSObjectSummary();
        obj3.setKey("file3.ts");

        // First page: truncated
        ListObjectsV2Result firstPage = mock(ListObjectsV2Result.class);
        when(firstPage.getObjectSummaries()).thenReturn(Arrays.asList(obj1, obj2));
        when(firstPage.isTruncated()).thenReturn(true);
        when(firstPage.getNextContinuationToken()).thenReturn("token-page2");

        // Second page: not truncated
        ListObjectsV2Result secondPage = mock(ListObjectsV2Result.class);
        when(secondPage.getObjectSummaries()).thenReturn(Collections.singletonList(obj3));
        when(secondPage.isTruncated()).thenReturn(false);

        when(mockOssClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(firstPage, secondPage);

        List<OSSObjectSummary> objects = ossService.listAllObjects("prefix/");

        assertEquals(3, objects.size());
        verify(mockOssClient, times(2)).listObjectsV2(any(ListObjectsV2Request.class));

        // Verify second request uses continuation token
        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(mockOssClient, times(2)).listObjectsV2(captor.capture());

        List<ListObjectsV2Request> requests = captor.getAllValues();
        assertNull(requests.get(0).getContinuationToken());
        assertEquals("token-page2", requests.get(1).getContinuationToken());
    }

    @Test
    void listAllObjects_emptyResult_shouldReturnEmptyList() {
        ListObjectsV2Result result = mock(ListObjectsV2Result.class);
        when(result.getObjectSummaries()).thenReturn(Collections.emptyList());
        when(result.isTruncated()).thenReturn(false);

        when(mockOssClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(result);

        List<OSSObjectSummary> objects = ossService.listAllObjects("empty-prefix/");

        assertTrue(objects.isEmpty());
    }

    // --- deleteObject ---

    @Test
    void deleteObject_shouldDelegateToBucketAndKey() {
        ossService.deleteObject("videos/raw/delete-me.mp4");

        verify(mockOssClient).deleteObject("test-video-bucket", "videos/raw/delete-me.mp4");
    }

    // --- setObjectAcl ---

    @Test
    void setObjectAcl_shouldPassParameters() {
        ossService.setObjectAcl("videos/raw/test.mp4", CannedAccessControlList.PublicRead);

        verify(mockOssClient).setObjectAcl(
                "test-video-bucket", "videos/raw/test.mp4", CannedAccessControlList.PublicRead);
    }

    // --- changeObjectStorageClass ---

    @Test
    void changeObjectStorageClass_shouldCopyWithNewMetadata() {
        ossService.changeObjectStorageClass("videos/raw/test.mp4", StorageClass.IA);

        ArgumentCaptor<CopyObjectRequest> captor =
                ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(mockOssClient).copyObject(captor.capture());

        CopyObjectRequest request = captor.getValue();
        assertEquals("test-video-bucket", request.getSourceBucketName());
        assertEquals("videos/raw/test.mp4", request.getSourceKey());
        assertEquals("test-video-bucket", request.getDestinationBucketName());
        assertEquals("videos/raw/test.mp4", request.getDestinationKey());
        assertNotNull(request.getNewObjectMetadata());
    }
}
