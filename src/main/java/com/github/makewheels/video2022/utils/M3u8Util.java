package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class M3u8Util {
    public static List<String> getUrlListFromM3u8(String m3u8Url) {
        String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
        String[] eachLine = HttpUtil.get(m3u8Url).split("\n");
        List<String> urlList = new ArrayList<>(eachLine.length + 1);
        urlList.add(m3u8Url);
        urlList.addAll(Arrays.stream(eachLine)
                .filter(e -> !e.startsWith("#")).map(e -> baseUrl + e)
                .collect(Collectors.toList()));
        return urlList;
    }
}
