package com.github.makewheels.video2022.file.md5;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * https://fcnext.console.aliyun.com/cn-beijing/services/video-2022-prod/function-detail/get-oss-object-md5/LATEST?tab=trigger
 * https://fcnext.console.aliyun.com/cn-beijing/services/video-2022-dev/function-detail/get-oss-object-md5/LATEST?tab=trigger
 */
@Slf4j
public class Md5CloudFunction implements HttpRequestHandler {
    // OSS挂载路径
    private final static String OSS_MOUNT_PATH = "/mnt/oss";

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context)
            throws IOException {
        // 拿到请求参数
        JSONObject body = JSON.parseObject(IoUtil.readUtf8(request.getInputStream()));
        log.info("body = " + body.toJSONString());

        // 要计算md5的文件
        String key = body.getString("key");
        File file = new File(OSS_MOUNT_PATH, key);
        log.info("文件路径：" + file.getAbsolutePath());
        log.info("文件大小：" + FileUtil.readableFileSize(file));

        // 计算md5
        log.info("开始计算MD5：");
        long startTime = System.currentTimeMillis();
        String md5 = DigestUtil.md5Hex(file);
        log.info("计算MD5结束，md5 = " + md5);
        log.info("耗时：" + (System.currentTimeMillis() - startTime) + "ms");

        //返回结果
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("aliyunRequestId", context.getRequestId());
        resultMap.put("key", key);
        resultMap.put("md5", md5);
        String result = JSON.toJSONString(resultMap);
        response.setHeader("Content-Type", "application/json");
        IoUtil.writeUtf8(response.getOutputStream(), true, result);
    }
}
