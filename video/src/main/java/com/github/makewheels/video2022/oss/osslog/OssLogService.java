package com.github.makewheels.video2022.oss.osslog;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.oss.osslog.bean.GenerateOssAccessLogDTO;
import com.github.makewheels.video2022.oss.osslog.bean.OssAccessLog;
import com.github.makewheels.video2022.oss.osslog.bean.OssAccessLogFile;
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
    private GenerateOssAccessLogDTO createGenerateOssLogDTO(LocalDate date) {
        GenerateOssAccessLogDTO generateOssAccessLogDTO = new GenerateOssAccessLogDTO();
        generateOssAccessLogDTO.setDate(date);
        String programBatchId = idService.nextLongId("oss_access_log_batch_");
        generateOssAccessLogDTO.setProgramBatchId(programBatchId);
        log.info("生成GenerateOssLogDTO = " + JSON.toJSONString(generateOssAccessLogDTO));
        return generateOssAccessLogDTO;
    }

    /**
     * 获取一天所有的，OSS日志文件的key
     * 日志文件名是北京时间，不是UTC时间
     * 文件名示例：video-2022-prod2023-06-18-14-00-00-0001
     *
     * @param date 传入日期，按prefix检索OSS文件
     */
    private List<String> getLogFileKeys(LocalDate date) {
        // video-2022-prod/accesslog/video-2022-prod2023-06-06
        String prefix = accesslogPrefix + "/" + ossVideoService.getBucket() + date;
        List<String> logFileKeys = ossDataService.listAllObjects(prefix).stream()
                .map(OSSObjectSummary::getKey).collect(Collectors.toList());
        log.info("获取到日志文件，大小 = " + logFileKeys.size()
                + ", logFileKeys = " + JSON.toJSONString(logFileKeys));
        return logFileKeys;
    }

    /**
     * 创建OssLogFile
     */
    private OssAccessLogFile createOssAccessLogFile(
            GenerateOssAccessLogDTO generateOssAccessLogDTO, String logFileKey) {
        OssAccessLogFile ossAccessLogFile = new OssAccessLogFile();
        ossAccessLogFile.setProgramBatchId(generateOssAccessLogDTO.getProgramBatchId());
        ossAccessLogFile.setLogDate(generateOssAccessLogDTO.getDate());
        ossAccessLogFile.setLogFileKey(logFileKey);
        // video-2022-prod2023-06-18-16-00-00-0001
        String filename = FilenameUtils.getName(logFileKey);
        ossAccessLogFile.setLogFileName(filename);
        // 2023-06-18-16-00-00-0001
        String timeAndSeq = filename.replace(ossVideoService.getBucket(), "");

        // 2023-06-18-16-00-00
        String timeString = StringUtils.substringBeforeLast(timeAndSeq, "-");
        Date logFileTime = DateUtil.parse(timeString, "yyyy-MM-dd-HH-mm-ss");

        // 0001
        String sequenceNumber = StringUtils.substringAfterLast(timeAndSeq, "-");

        ossAccessLogFile.setLogFileTime(logFileTime);
        ossAccessLogFile.setLogFileSequenceNumber(sequenceNumber);
        return ossAccessLogFile;
    }

    /**
     * 解析日志文件
     */
    private List<OssAccessLog> parseOssAccessLogFile(
            OssAccessLogFile ossAccessLogFile, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        // 下载文件
        String logContent = ossDataService.getObjectContent(ossAccessLogFile.getLogFileKey());
        log.info("日志文件大小：" + FileUtil.readableFileSize(logContent.length()));
        List<String> lines = Arrays.asList(logContent.split("\n"));

        // TODO 解析不是分割，要从前往后逐步解析
        List<OssAccessLog> ossAccessLogList = new ArrayList<>(lines.size());
        for (String line : lines) {
            line = line.trim();
            line = line.replace("[", "\"");
            line = line.replace("]", "\"");
            List<String> row = Arrays.asList(line.split(" (?=([^']*'[^']*')*[^']*$)"));
            OssAccessLog ossAccessLog = new OssAccessLog();
            ossAccessLog.setProgramBatchId(generateOssAccessLogDTO.getProgramBatchId());
            ossAccessLog.setLogFileId(ossAccessLogFile.getId());

            ossAccessLog.setRemoteIp(row.get(0));
            ossAccessLog.setReserved1(row.get(1));
            ossAccessLog.setReserved2(row.get(2));
            ossAccessLog.setTime(DateUtil.parse(row.get(3), "dd/MMM/yyyy:HH:mm:ss Z"));
            ossAccessLog.setRequestUrl(row.get(4));
            ossAccessLog.setHttpStatus(Integer.parseInt(row.get(5)));
            ossAccessLog.setSentBytes(Long.parseLong(row.get(6)));
            ossAccessLog.setRequestTime(Long.parseLong(row.get(7)));
            ossAccessLog.setReferer(row.get(8));
            ossAccessLog.setUserAgent(row.get(9));
            ossAccessLog.setHostName(row.get(10));
            ossAccessLog.setRequestId(row.get(11));
            ossAccessLog.setLoggingFlag(Boolean.parseBoolean(row.get(12)));
            ossAccessLog.setRequesterAliyunId(row.get(13));
            ossAccessLog.setOperation(row.get(14));
            ossAccessLog.setBucketName(row.get(15));
            ossAccessLog.setObjectName(row.get(16));
            ossAccessLog.setObjectSize(Long.parseLong(row.get(17)));
            ossAccessLog.setServerCostTime(Long.parseLong(row.get(18)));
            ossAccessLog.setErrorCode(row.get(19));
            ossAccessLog.setRequestLength(Integer.parseInt(row.get(20)));
            ossAccessLog.setUserId(row.get(21));
            ossAccessLog.setDeltaDataSize(Long.parseLong(row.get(22)));
            ossAccessLog.setSyncRequest(row.get(23));
            ossAccessLog.setStorageClass(row.get(24));
            ossAccessLog.setTargetStorageClass(row.get(25));
            ossAccessLog.setTransmissionAccelerationAccessPoint(row.get(26));
            ossAccessLog.setAccessKeyId(row.get(27));
            ossAccessLogList.add(ossAccessLog);
        }
        return ossAccessLogList;
    }

    /**
     * 处理一个日志文件
     */
    private void handleLogFile(String logFileKey, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        log.info("开始处理日志文件logFileKey = " + logFileKey);
        // 如果文件已经解析过，跳过
        if (ossLogRepository.isOssLogFileKeyExists(logFileKey)) {
            log.info("数据库OssLogFile已存在该日志文件跳过，key = " + logFileKey);
            return;
        }

        // 生成ossLogFile
        OssAccessLogFile ossAccessLogFile = createOssAccessLogFile(generateOssAccessLogDTO, logFileKey);
        mongoTemplate.save(ossAccessLogFile);
        log.info("保存ossAccessLogFile " + JSON.toJSONString(ossAccessLogFile));

        // 解析日志文件
        List<OssAccessLog> ossAccessLogs = parseOssAccessLogFile(ossAccessLogFile, generateOssAccessLogDTO);
        mongoTemplate.insertAll(ossAccessLogs);
        log.info("保存ossAccessLogs，总数：" + ossAccessLogs.size());
    }

    public void generateOssAccessLog(LocalDate date) {
        log.info("开始获取OSS访问日志，date = " + date);
        // 创建DTO
        GenerateOssAccessLogDTO generateOssAccessLogDTO = createGenerateOssLogDTO(date);

        // 获取日志文件key
        List<String> logFileKeys = getLogFileKeys(date);

        // 解析每个日志文件
        for (String logFileKey : logFileKeys) {
            handleLogFile(logFileKey, generateOssAccessLogDTO);
        }
        log.info("生成OSS访问日志完成，date = " + date);
    }

}
