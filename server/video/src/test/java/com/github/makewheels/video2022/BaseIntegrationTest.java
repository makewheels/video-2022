package com.github.makewheels.video2022;

import com.github.makewheels.video2022.file.md5.Md5CfService;
import com.github.makewheels.video2022.oss.service.OssDataService;
import com.github.makewheels.video2022.oss.service.OssVideoService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.utils.IpService;
import com.github.makewheels.video2022.video.service.YoutubeService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for integration tests.
 * <p>
 * Uses real MongoDB (video-2022-test), but mocks all external HTTP services
 * (OSS, MPS, IP API, cloud functions, YouTube).
 * <p>
 * Subclasses should call {@link #cleanDatabase()} in @BeforeEach if they need a clean state.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MongoTemplate mongoTemplate;

    // Mock all external services to prevent real HTTP calls
    @MockitoBean
    protected OssVideoService ossVideoService;

    @MockitoBean
    protected OssDataService ossDataService;

    @MockitoBean
    protected AliyunMpsService aliyunMpsService;

    @MockitoBean
    protected CloudFunctionTranscodeService cloudFunctionTranscodeService;

    @MockitoBean
    protected IpService ipService;

    @MockitoBean
    protected Md5CfService md5CfService;

    @MockitoBean
    protected YoutubeService youtubeService;

    /**
     * Drop all collections in the test database to ensure isolation between tests.
     */
    protected void cleanDatabase() {
        for (String name : mongoTemplate.getCollectionNames()) {
            mongoTemplate.dropCollection(name);
        }
    }
}
