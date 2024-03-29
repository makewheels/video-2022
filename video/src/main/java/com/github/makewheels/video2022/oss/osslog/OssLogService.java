package com.github.makewheels.video2022.oss.osslog;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
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
import java.util.*;
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
    @Resource
    private EnvironmentService environmentService;

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
        log.info("获取到日志文件，大小 = " + logFileKeys.size());
        log.info("logFileKeys: " + JSON.toJSONString(logFileKeys));
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
        String uniqueString = StringUtils.substringAfterLast(timeAndSeq, "-");

        ossAccessLogFile.setLogFileTime(logFileTime);
        ossAccessLogFile.setLogFileUniqueString(uniqueString);
        return ossAccessLogFile;
    }

    /**
     * 解析一行日志
     * 39.107.7.48 - - [07/Jun/2023:07:16:10 +0800]
     * "GET /?acl HTTP/1.1" 200 255 32 "-"
     * "aliyun-sdk-java/3.10.2(Linux/2.6.32-220.23.2.ali927.el5.x86_64/amd64;1.8.0_152)"
     * "video-2022-prod.oss-cn-beijing.aliyuncs.com" "647FBE3AB258223337A7F306"
     * "true" "302503994806979967" "GetBucketAcl" "video-2022-prod" "-" - -
     * "-" 926 "1618784280874658" - "-" "standard" "-" "-" "STS.NUc7FvPnunUNYKsBUN4KoFoft"
     */
    private List<String> readLine(String line) {
        // 去掉第一个空格
        line = line.substring(1);
        // 把中括号[] 替换为 双引号""
        line = line.replace(" [", " \"");
        line = line.replace("] ", "\" ");

        // 把双引号之间的空格，替换为特殊字符
        StringBuilder stringBuilder = new StringBuilder(line);
        char specialSpace = '^';
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            }
            if (c == ' ' && inQuote) {
                stringBuilder.setCharAt(i, specialSpace);
            }
        }

        // 按空格分割，再把特殊字符替换回空格，返回数组
        String[] split = stringBuilder.toString().split(" ");
        List<String> result = new ArrayList<>(split.length);
        for (String str : split) {
            str = str.replace(specialSpace, ' ');
            str = str.replace("\"", "");
            result.add(str);
        }
        return result;
    }

    /**
     * 从日志文件中，解析出每行日志
     */
    private List<OssAccessLog> parseLogLines(
            OssAccessLogFile ossAccessLogFile, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        String logContent = ossDataService.getObjectContent(ossAccessLogFile.getLogFileKey());
        log.info("下载日志文件，大小：" + FileUtil.readableFileSize(logContent.length()));

        List<String> lines = Arrays.asList(logContent.split("\n"));
        List<OssAccessLog> ossAccessLogList = new ArrayList<>(lines.size());
        for (String line : lines) {
            List<String> row = readLine(line);
            OssAccessLog ossAccessLog = new OssAccessLog();
            ossAccessLog.setProgramBatchId(generateOssAccessLogDTO.getProgramBatchId());
            ossAccessLog.setLogFileId(ossAccessLogFile.getId());
            ossAccessLog.setLine(line);
            ossAccessLog.setMd5(DigestUtil.md5Hex(line));
            ossAccessLog.setRemoteIp(row.get(0));
            ossAccessLog.setReserved1(row.get(1));
            ossAccessLog.setReserved2(row.get(2));
            ossAccessLog.setTime(DateUtil.parse(row.get(3), "dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH));
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
            ossAccessLog.setObjectSize(row.get(17).equals("-") ? 0 : Long.parseLong(row.get(17)));
            ossAccessLog.setServerCostTime(row.get(18).equals("-") ? 0 : Long.parseLong(row.get(18)));
            ossAccessLog.setErrorCode(row.get(19));
            ossAccessLog.setRequestLength(Integer.parseInt(row.get(20)));
            ossAccessLog.setUserId(row.get(21));
            ossAccessLog.setDeltaDataSize(row.get(22).equals("-") ? 0 : Long.parseLong(row.get(22)));
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
     * 解析日志文件
     * <a href="https://help.aliyun.com/zh/oss/user-guide/logging">日志转存</a>
     */
    private void parseAndSaveLogLine(
            OssAccessLogFile ossAccessLogFile, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        List<OssAccessLog> ossAccessLogs = parseLogLines(ossAccessLogFile, generateOssAccessLogDTO);
        log.info("开始保存ossAccessLogs，总数：" + ossAccessLogs.size());
        for (OssAccessLog ossAccessLog : ossAccessLogs) {
            if (ossLogRepository.isOssLogLineMd5Exists(ossAccessLog.getMd5())) {
                log.info("数据库OssAccessLog已存在该日志跳过，md5 = " + ossAccessLog.getMd5());
                continue;
            }
            mongoTemplate.save(ossAccessLog);
        }
    }

    /**
     * 日志文件key是否已存在
     */
    private boolean isLogFileNeedSkip(String logFileKey) {
        // 开发环境不跳过
        if (environmentService.isDevelopmentEnv()) {
            return false;
        }
        if (ossLogRepository.isOssLogFileKeyExists(logFileKey)) {
            log.info("数据库OssLogFile已存在该日志文件跳过，key = " + logFileKey);
            return true;
        }
        return false;
    }

    /**
     * 处理单个日志文件
     */
    private OssAccessLogFile parseAndSaveLogFile(String logFileKey, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        // 生成ossLogFile
        OssAccessLogFile ossAccessLogFile = createOssAccessLogFile(generateOssAccessLogDTO, logFileKey);
        mongoTemplate.save(ossAccessLogFile);
        log.info("保存ossAccessLogFile " + JSON.toJSONString(ossAccessLogFile));
        return ossAccessLogFile;
    }

    /**
     * 根据key解析保存日志
     */
    private void handleLogFileKeys(List<String> logFileKeys, GenerateOssAccessLogDTO generateOssAccessLogDTO) {
        for (String logFileKey : logFileKeys) {
            log.info("开始处理日志文件logFileKey = " + logFileKey);
            if (isLogFileNeedSkip(logFileKey)) {
                continue;
            }
            // 保存日志文件
            OssAccessLogFile logFile = parseAndSaveLogFile(logFileKey, generateOssAccessLogDTO);

            // 保存日志记录
            parseAndSaveLogLine(logFile, generateOssAccessLogDTO);
        }
    }

    /**
     * 根据时间生成OSS访问日志
     */
    public void generateOssAccessLog(LocalDate date) {
        log.info("开始获取OSS访问日志，date = " + date);
        // 创建DTO
        GenerateOssAccessLogDTO generateOssAccessLogDTO = createGenerateOssLogDTO(date);

        // 获取日志文件key
        List<String> logFileKeys = getLogFileKeys(date);

        // 根据key解析保存日志
        handleLogFileKeys(logFileKeys, generateOssAccessLogDTO);
        log.info("生成OSS访问日志完成，date = " + date);
    }

}
