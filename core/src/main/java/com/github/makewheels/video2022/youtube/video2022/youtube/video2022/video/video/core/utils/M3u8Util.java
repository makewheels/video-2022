package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.utils;

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
}
