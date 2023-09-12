package com.github.makewheels.video2022.file.md5;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import com.github.makewheels.video2022.etc.system.context.RequestUtil;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云云函数：获取OSS文件MD5
 * <a href="https://fcnext.console.aliyun.com/cn-beijing/services/video-2022-prod/function-detail/get-oss-object-md5/LATEST?tab=trigger">prod获取md5云函数</a>
 * <a href="https://fcnext.console.aliyun.com/cn-beijing/services/video-2022-dev/function-detail/get-oss-object-md5/LATEST?tab=trigger">dev获取md5云函数</a>
 */
@Slf4j
public class Md5CloudFunction implements HttpRequestHandler {
    // OSS挂载路径
    private final static String OSS_MOUNT_PATH = "/mnt/oss";

    /**
     * 云函数根据挂载的OSS文件计算MD5
     */
    private String getMd5(String key) {
        File file = new File(OSS_MOUNT_PATH, key);
        log.info("文件路径：" + file.getAbsolutePath());
        long startTime = System.currentTimeMillis();
        String md5 = DigestUtil.md5Hex(file);
        log.info("计算MD5 = " + md5
                + "，文件大小：" + FileUtil.readableFileSize(file)
                + "，耗时：" + (System.currentTimeMillis() - startTime) + "ms");
        return md5;
    }

    /**
     * 响应结果
     */
    private void responseResult(JSONObject body, HttpServletResponse response, Map<String, Object> resultMap)
            throws IOException {
        String result = JSON.toJSONString(resultMap);
        log.info("响应结果：" + result);
        // 同步返回
        if (!body.getBoolean("callback")) {
            response.setHeader("Content-Type", "application/json");
            IoUtil.writeUtf8(response.getOutputStream(), true, result);
            return;
        }
        // 异步回调
        String callbackUrl = body.getString("callbackUrl");
        HttpUtil.post(callbackUrl, result);
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context)
            throws IOException {
        JSONObject body = RequestUtil.servletToRequestJSONObject(request);
        log.info("请求body = " + body.toJSONString());

        List<FileMd5DTO> objectList = JSONArray.parseArray(
                JSON.toJSONString(body.getJSONArray("objectList")),
                FileMd5DTO.class);
        for (FileMd5DTO fileMd5DTO : objectList) {
            String md5 = getMd5(fileMd5DTO.getKey());
            fileMd5DTO.setMd5(md5);
        }

        //返回结果
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("aliyunRequestId", context.getRequestId());
        resultMap.put("objectList", objectList);
        responseResult(body, response, resultMap);
    }

}
