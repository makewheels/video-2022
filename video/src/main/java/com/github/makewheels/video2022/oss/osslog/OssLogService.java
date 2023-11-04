package com.github.makewheels.video2022.oss.osslog;

import cn.hutool.core.date.DateUtil;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.oss.osslog.bean.GenerateOssLogDTO;
import com.github.makewheels.video2022.oss.osslog.bean.OssLog;
import com.github.makewheels.video2022.oss.osslog.bean.OssLogFile;
import com.github.makewheels.video2022.oss.service.OssDataService;
import com.github.makewheels.video2022.oss.service.OssVideoService;
import com.github.makewheels.video2022.utils.IdService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * oss日志解析
 */
@Service
@Slf4j
public class OssLogService {
    @Value("${aliyun.oss.data.accesslog-prefix}")
    private String accesslogPrefix;
    @Resource
    private OssDataService ossDataService;
    @Resource
    private OssVideoService ossVideoService;
    @Resource
    private IdService idService;
    @Resource
    private OssLogRepository ossLogRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 创建DTO
     */
    private GenerateOssLogDTO createGenerateOssLogDTO(LocalDate date) {
        GenerateOssLogDTO generateOssLogDTO = new GenerateOssLogDTO();
        generateOssLogDTO.setDate(date);
        String programBatchId = idService.nextLongId("oss_log_batch");
        generateOssLogDTO.setProgramBatchId(programBatchId);
        return generateOssLogDTO;
    }

    /**
     * 获取一天所有的，OSS日志文件的key
     * 日志文件名是北京时间，不是UTC时间
     * 文件名示例：video-2022-prod2023-06-18-14-00-00-0001
     *
     * @param date 传入日期，按prefix检索OSS文件
     */
    private List<String> getLogFileKeys(LocalDate date) {
        String prefix = accesslogPrefix + "/" + ossVideoService.getBucket() + date;
        return ossDataService.listAllObjects(prefix).stream()
                .map(OSSObjectSummary::getKey).collect(Collectors.toList());
    }

    /**
     * 创建OssLogFile
     */
    private OssLogFile createOssLogFile(GenerateOssLogDTO generateOssLogDTO, String logFileKey) {
        OssLogFile ossLogFile = new OssLogFile();
        ossLogFile.setProgramBatchId(generateOssLogDTO.getProgramBatchId());
        ossLogFile.setLogDate(generateOssLogDTO.getDate());
        ossLogFile.setLogFileKey(logFileKey);
        // video-2022-prod2023-06-18-16-00-00-0001
        String filename = FilenameUtils.getName(logFileKey);
        ossLogFile.setLogFileName(filename);
        // 2023-06-18-16-00-00-0001
        String timeAndUniqueString = filename.replace(ossVideoService.getBucket(), "");

        // 2023-06-18-16-00-00
        String timeString = StringUtils.substringBeforeLast(timeAndUniqueString, "-");
        Date logFileTime = DateUtil.parse(timeString, "yyyy-MM-dd-HH-mm-ss");

        // 0001
        String uniqueString = StringUtils.substringAfterLast(timeAndUniqueString, "-");

        ossLogFile.setLogFileTime(logFileTime);
        ossLogFile.setLogFileUniqueString(uniqueString);
        ossLogFile.setCreateTime(new Date());
        ossLogFile.setUpdateTime(new Date());
        return ossLogFile;
    }

    /**
     * 解析日志文件
     */
    private List<OssLog> parseOssLogFile(OssLogFile ossLogFile) {
        // 下载文件
        String logContent = ossDataService.getObjectContent(ossLogFile.getLogFileKey());
        List<String> lines = Arrays.asList(logContent.split("\n"));

        // TODO 解析不是分割，要从前往后逐步解析
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

    private void generateOssLog(LocalDate date) {
        // 创建DTO
        GenerateOssLogDTO generateOssLogDTO = createGenerateOssLogDTO(date);

        // 获取日志文件key
        List<String> logFileKeys = getLogFileKeys(date);

        // 文件处理每个日志文件
        for (String logFileKey : logFileKeys) {
            // 如果文件已经解析过，跳过
            if (ossLogRepository.isOssLogFileKeyExists(logFileKey)) {
                continue;
            }
            OssLogFile ossLogFile = createOssLogFile(generateOssLogDTO, logFileKey);
            mongoTemplate.save(ossLogFile);
            List<OssLog> ossLogs = parseOssLogFile(ossLogFile);
            mongoTemplate.insertAll(ossLogs);
        }
    }

}
