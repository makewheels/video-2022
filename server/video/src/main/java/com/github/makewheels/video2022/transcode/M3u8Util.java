package com.github.makewheels.video2022.transcode;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class M3u8Util {
    /**
     * 获取文件名列表
     */
    public static List<String> getFilenames(String m3u8Content) {
        return Arrays.stream(m3u8Content.split("\n"))
                .filter(e -> !e.startsWith("#")).collect(Collectors.toList());
    }

    /**
     * 获取ts时长
     *
     * @return 638e1d389cae0b13419384b6-00000.ts -> 5.338667
     */
    public static Map<String, BigDecimal> getTsTimeLengthMap(String m3u8Content) {
        String[] lines = m3u8Content.split("\n");

        List<Integer> EXTINF_indexes = new ArrayList<>((lines.length - 5) / 2);
        //找到以 #EXTINF: 开头的索引
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("#EXTINF:")) {
                EXTINF_indexes.add(i);
            }
        }

        //组装map返回
        Map<String, BigDecimal> map = new HashMap<>(EXTINF_indexes.size());
        for (Integer index : EXTINF_indexes) {
            String EXTINF = StringUtils.substringBetween(lines[index], "#EXTINF:", ",");
            BigDecimal timeLength = new BigDecimal(EXTINF);
            String filename = lines[index + 1];
            map.put(filename, timeLength);
        }
        return map;
    }

    /**
     * 获取 fMP4 的 init segment URI（#EXT-X-MAP:URI="init.mp4"），TS playlist 返回 null。
     * 兼容带引号与属性顺序差异，CRLF 由调用方先归一。
     */
    public static String getInitSegmentUri(String m3u8Content) {
        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#EXT-X-MAP:")) continue;
            int idx = trimmed.indexOf("URI=");
            if (idx < 0) continue;
            String rest = trimmed.substring(idx + 4).trim();
            if (rest.length() >= 2 && rest.charAt(0) == '"' && rest.charAt(rest.length() - 1) == '"') {
                rest = rest.substring(1, rest.length() - 1);
            } else {
                // 无引号时到逗号或行尾
                int comma = rest.indexOf(',');
                if (comma >= 0) rest = rest.substring(0, comma);
            }
            return rest.isEmpty() ? null : rest;
        }
        return null;
    }
}
