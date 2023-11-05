package com.github.makewheels.video2022.file.access;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.finance.fee.UnitName;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFee;
import com.github.makewheels.video2022.finance.unitprice.UnitPrice;
import com.github.makewheels.video2022.finance.unitprice.UnitPriceService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Service
@Slf4j
public class FileAccessLogService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TsFileRepository tsFileRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private UnitPriceService unitPriceService;

    public void handleAccessLog(
            HttpServletRequest request, String videoId, String clientId, String sessionId,
            String resolution, String fileId) {
        FileAccessLog fileAccessLog = saveAccessLog(request, videoId, clientId, sessionId, resolution, fileId);
        createFee(fileAccessLog, request, videoId, clientId, sessionId, resolution, fileId);
    }

    /**
     * 保存文件访问记录
     */
    public FileAccessLog saveAccessLog(
            HttpServletRequest request, String videoId, String clientId, String sessionId,
            String resolution, String fileId) {
        TsFile tsFile = tsFileRepository.getById(fileId);
        String transcodeId = tsFile.getTranscodeId();
        Transcode transcode = transcodeRepository.getById(transcodeId);

        FileAccessLog accessLog = new FileAccessLog();
        BeanUtils.copyProperties(tsFile, accessLog);
        accessLog.setId(null);
        accessLog.setFileId(tsFile.getId());
        accessLog.setFileType(tsFile.getFileType());

        accessLog.setClientId(clientId);
        accessLog.setSessionId(sessionId);
        accessLog.setTranscodeId(transcode.getId());
        accessLog.setResolution(transcode.getResolution());
        accessLog.setCreateTime(new Date());
        //TODO 拿不到ip
        accessLog.setIp(request.getRemoteAddr());

        mongoTemplate.save(accessLog);
        log.debug("保存文件访问记录 " + JSON.toJSONString(accessLog));
        return accessLog;
    }

    /**
     * 计费
     */
    public void createFee(FileAccessLog fileAccessLog, HttpServletRequest request,
                          String videoId, String clientId, String sessionId,
                          String resolution, String fileId) {
        OssAccessFee ossAccessFee = new OssAccessFee();
        ossAccessFee.setUserId(fileAccessLog.getUserId());
        ossAccessFee.setVideoId(videoId);
        ossAccessFee.setUnitName(UnitName.GB);
        UnitPrice unitPrice = unitPriceService.getOssAccessUnitPrice(fileAccessLog.getCreateTime());
        ossAccessFee.setUnitPrice(unitPrice.getUnitPrice());
        ossAccessFee.setAmount(BigDecimal.valueOf(fileAccessLog.getSize()));
        BigDecimal feePrice = ossAccessFee.getUnitPrice()
                .multiply(ossAccessFee.getAmount())
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN);
        ossAccessFee.setFeePrice(feePrice);

        ossAccessFee.setFileId(fileId);
        ossAccessFee.setAccessId(fileAccessLog.getId());
        ossAccessFee.setKey(fileAccessLog.getKey());
        ossAccessFee.setStorageClass(fileAccessLog.getStorageClass());
        ossAccessFee.setFileSize(fileAccessLog.getSize());
        ossAccessFee.setBillTime(fileAccessLog.getCreateTime());

        mongoTemplate.save(ossAccessFee);
        log.info("保存OSS访问费用 " + JSON.toJSONString(ossAccessFee));
    }
}
