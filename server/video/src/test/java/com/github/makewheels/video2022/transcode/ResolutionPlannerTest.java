package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.pipeline.ResolutionPlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 选档与处理方决策（T01/T02/T05 相关）：显示短边选档、不放大、不重复档、无 480p 新档。
 */
class ResolutionPlannerTest {

    private ResolutionPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new ResolutionPlanner();
        ReflectionTestUtils.setField(planner, "maxDirectBitrateBps", 13_000_000L);
    }

    private JSONObject media(int displayWidth, int displayHeight) {
        JSONObject m = new JSONObject();
        m.put("displayWidth", displayWidth);
        m.put("displayHeight", displayHeight);
        m.put("videoCodec", "h264");
        m.put("audioCodec", "aac");
        m.put("hasAudio", true);
        m.put("bitrateBps", 5_000_000L);
        m.put("pixFmt", "yuv420p");
        m.put("dynamicRange", "SDR");
        return m;
    }

    @Test
    void planResolutions_360p_singleLane720pLabel() {
        List<String> lanes = planner.planResolutions(360);
        assertEquals(List.of("720p"), lanes);
        // 不放大：输出尺寸 = 原尺寸
        int[] size = planner.computeOutputSize(640, 360, "720p");
        assertArrayEquals(new int[]{640, 360}, size);
    }

    @Test
    void planResolutions_720_singleLane() {
        assertEquals(List.of("720p"), planner.planResolutions(720));
        // 不缩放
        int[] size = planner.computeOutputSize(1280, 720, "720p");
        assertArrayEquals(new int[]{1280, 720}, size);
    }

    @Test
    void planResolutions_900_shortSide720() {
        assertEquals(List.of("720p"), planner.planResolutions(900));
        int[] size = planner.computeOutputSize(1600, 900, "720p");
        assertArrayEquals(new int[]{1280, 720}, size);
    }

    @Test
    void planResolutions_1080_twoLanes() {
        assertEquals(List.of("720p", "1080p"), planner.planResolutions(1080));
    }

    @Test
    void planResolutions_2160_twoLanesNo4k() {
        List<String> lanes = planner.planResolutions(2160);
        assertEquals(List.of("720p", "1080p"), lanes);
        int[] size = planner.computeOutputSize(3840, 2160, "1080p");
        assertArrayEquals(new int[]{1920, 1080}, size);
    }

    @Test
    void portraitVideo_shortSideRule() {
        // 1080x1920 竖屏：短边 1080 → 两档；720p 档输出 720x1280
        assertEquals(List.of("720p", "1080p"), planner.planResolutions(1080));
        int[] size = planner.computeOutputSize(1080, 1920, "720p");
        assertArrayEquals(new int[]{720, 1280}, size);
        int[] size1080 = planner.computeOutputSize(1080, 1920, "1080p");
        assertArrayEquals(new int[]{1080, 1920}, size1080);
    }

    @Test
    void oddDimension_evenAligned() {
        int[] size = planner.computeOutputSize(1281, 721, "720p");
        assertEquals(0, size[0] % 2);
        assertEquals(0, size[1] % 2);
        assertEquals(720, size[1]);
    }

    @Test
    void provider_h264Compliant_remuxOnCpu() {
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU,
                planner.decideProvider(media(1280, 720), "720p"));
        assertFalse(planner.needReencode(media(1280, 720), "720p"));
    }

    @Test
    void provider_nonH264_gpu() {
        JSONObject m = media(1920, 1080);
        m.put("videoCodec", "hevc");
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU, planner.decideProvider(m, "1080p"));
    }

    @Test
    void provider_needsScale_gpu() {
        // 1080 源 720p 档需要缩放 → GPU；1080p 档原尺寸 → remux
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU,
                planner.decideProvider(media(1920, 1080), "720p"));
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU,
                planner.decideProvider(media(1920, 1080), "1080p"));
    }

    @Test
    void provider_highBitrate_gpu() {
        JSONObject m = media(1920, 1080);
        m.put("bitrateBps", 20_000_000L);
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU, planner.decideProvider(m, "1080p"));
    }

    @Test
    void provider_hdr_gpu_neverRemux() {
        for (String dr : java.util.Arrays.asList("HDR10", "HLG", "PQ", "DOLBY_VISION", "UNKNOWN", null)) {
            JSONObject m = media(1920, 1080);
            m.put("dynamicRange", dr);
            m.put("videoCodec", dr == null ? "h264" : "hevc");
            assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU,
                    planner.decideProvider(m, "1080p"), "dynamicRange = " + dr);
        }
    }

    @Test
    void provider_422Sampling_gpu() {
        JSONObject m = media(1920, 1080);
        m.put("pixFmt", "yuv422p");
        assertEquals(TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU, planner.decideProvider(m, "1080p"));
    }

    @Test
    void mediaPolicy_hdrUsesHevcFmp4_sdrUsesH264Ts() {
        JSONObject hdr = media(1920, 1080);
        hdr.put("dynamicRange", "HDR10");
        hdr.put("videoCodec", "hevc");
        JSONObject hdrPolicy = planner.buildMediaPolicy(hdr, "1080p");
        assertEquals("hevc", hdrPolicy.getString("videoCodec"));
        assertEquals("fmp4", hdrPolicy.getString("segmentType"));
        assertEquals(12_000_000L, hdrPolicy.getLongValue("targetBitrateBps"));

        JSONObject sdrPolicy = planner.buildMediaPolicy(media(1920, 1080), "720p");
        assertEquals("h264", sdrPolicy.getString("videoCodec"));
        assertEquals("ts", sdrPolicy.getString("segmentType"));
        assertEquals(4_000_000L, sdrPolicy.getLongValue("targetBitrateBps"));
        assertEquals(6_000_000L, sdrPolicy.getLongValue("maxBitrateBps"));
        assertEquals(12_000_000L, sdrPolicy.getLongValue("bufferBits"));
        assertEquals("preserve", sdrPolicy.getString("frameRateMode"));
        // 音频保留全部音轨，不强制单声道
        assertEquals(true, sdrPolicy.getJSONObject("audioPolicy").getBoolean("preserveTracks"));
    }
}
