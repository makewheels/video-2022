package com.github.makewheels.video2022.oss.osslog;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * oss日志解析
 */
@Service
public class OssLogService {
    public List<OssLog> parse(File file) {
        List<String> lines = FileUtil.readLines(file, StandardCharsets.UTF_8);
        List<OssLog> ossLogList = new ArrayList<>(lines.size());
        for (String line : lines) {
            line = line.trim();
            line = line.replace("[", "\"");
            line = line.replace("]", "\"");
            List<String> row = Arrays.asList(line.split(" (?=([^']*'[^']*')*[^']*$)"));
            OssLog ossLog = new OssLog();
            ossLog.setRemoteIp(row.get(0));
            ossLog.setReserved1(row.get(1));
            ossLog.setReserved2(row.get(2));
            ossLog.setTime(DateUtil.parse(row.get(3), "dd/MMM/yyyy:HH:mm:ss Z"));
            ossLog.setRequestUrl(row.get(4));
            ossLog.setHttpStatus(Integer.parseInt(row.get(5)));
            ossLog.setSentBytes(Long.parseLong(row.get(6)));
            ossLog.setRequestTime(Long.parseLong(row.get(7)));
            ossLog.setReferer(row.get(8));
            ossLog.setUserAgent(row.get(9));
            ossLog.setHostName(row.get(10));
            ossLog.setRequestId(row.get(11));
            ossLog.setLoggingFlag(Boolean.parseBoolean(row.get(12)));
            ossLog.setRequesterAliyunId(row.get(13));
            ossLog.setOperation(row.get(14));
            ossLog.setBucketName(row.get(15));
            ossLog.setObjectName(row.get(16));
            ossLog.setObjectSize(Long.parseLong(row.get(17)));
            ossLog.setServerCostTime(Long.parseLong(row.get(18)));
            ossLog.setErrorCode(row.get(19));
            ossLog.setRequestLength(Integer.parseInt(row.get(20)));
            ossLog.setUserId(row.get(21));
            ossLog.setDeltaDataSize(Long.parseLong(row.get(22)));
            ossLog.setSyncRequest(row.get(23));
            ossLog.setStorageClass(row.get(24));
            ossLog.setTargetStorageClass(row.get(25));
            ossLog.setTransmissionAccelerationAccessPoint(row.get(26));
            ossLog.setAccessKeyId(row.get(27));
            ossLogList.add(ossLog);
        }
        return ossLogList;
    }

    public static void main(String[] args) {
        File file = new File("C:\\Users\\thedoflin\\Downloads\\" +
                "video-2022-prod2023-09-06-09-00-00-0001");
        List<OssLog> ossLogList = new OssLogService().parse(file);
        for (OssLog ossLog : ossLogList) {
            System.out.println(JSON.toJSONString(ossLog, true));
        }
    }
}
