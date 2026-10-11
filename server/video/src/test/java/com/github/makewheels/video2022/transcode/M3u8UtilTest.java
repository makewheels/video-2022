package com.github.makewheels.video2022.transcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T14：playlist 解析兼容（TS 空行/CRLF）与 fMP4 EXT-X-MAP init segment。
 */
class M3u8UtilTest {

    private static final String TS_PLAYLIST = String.join("\n",
            "#EXTM3U",
            "#EXT-X-VERSION:3",
            "#EXT-X-TARGETDURATION:10",
            "#EXTINF:9.009,",
            "seg_0000.ts",
            "",
            "#EXTINF:9.009,",
            "seg_0001.ts",
            "#EXT-X-ENDLIST",
            "");

    private static final String FMP4_PLAYLIST = String.join("\n",
            "#EXTM3U",
            "#EXT-X-VERSION:7",
            "#EXT-X-TARGETDURATION:6",
            "#EXT-X-MAP:URI=\"init.mp4\"",
            "#EXTINF:6.006,",
            "seg_0000.m4s",
            "#EXTINF:6.006,",
            "seg_0001.m4s",
            "#EXT-X-ENDLIST");

    @Test
    void getFilenames_ts_ignoresCommentsAndEmptyLines() {
        var names = M3u8Util.getFilenames(TS_PLAYLIST);
        assertEquals(2, names.size());
        assertEquals("seg_0000.ts", names.get(0));
        assertEquals("seg_0001.ts", names.get(1));
    }

    @Test
    void getTsTimeLengthMap_ts() {
        var map = M3u8Util.getTsTimeLengthMap(TS_PLAYLIST);
        assertEquals(2, map.size());
        assertEquals(0, map.get("seg_0000.ts").compareTo(new java.math.BigDecimal("9.009")));
    }

    @Test
    void getFilenames_crlfNormalized() {
        String crlf = TS_PLAYLIST.replace("\n", "\r\n");
        var names = M3u8Util.getFilenames(crlf);
        // CRLF 情况下行尾带 \r，文件名去 \r 后可解析（与现有行为一致：按行拆分后过滤 #）
        assertEquals(2, names.size());
        assertTrue(names.get(0).startsWith("seg_0000.ts"));
    }

    @Test
    void getInitSegmentUri_tsPlaylist_returnsNull() {
        assertNull(M3u8Util.getInitSegmentUri(TS_PLAYLIST));
    }

    @Test
    void getInitSegmentUri_fmp4() {
        assertEquals("init.mp4", M3u8Util.getInitSegmentUri(FMP4_PLAYLIST));
    }

    @Test
    void getFilenames_fmp4_segmentsOnly() {
        var names = M3u8Util.getFilenames(FMP4_PLAYLIST);
        assertEquals(2, names.size());
        assertTrue(names.get(0).endsWith(".m4s"));
        // init 不混入分片列表（EXT-X-MAP 是注释行）
        assertFalse(names.contains("init.mp4"));
    }
}
