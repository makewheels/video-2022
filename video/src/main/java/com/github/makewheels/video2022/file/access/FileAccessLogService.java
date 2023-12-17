package com.github.makewheels.video2022.file.access;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFee;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFeeService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
    private VideoRepository videoRepository;
    @Resource
    private OssAccessFeeService ossAccessFeeService;

    public void handleAccessLog(
            HttpServletRequest request, String videoId, String clientId, String sessionId,
            String resolution, String fileId) {
        FileAccessLog fileAccessLog = saveAccessLog(
                request, videoId, clientId, sessionId, resolution, fileId);
        saveFee(fileAccessLog, request, videoId, clientId, sessionId, resolution, fileId);
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
        Video video = videoRepository.getById(videoId);
        accessLog.setVideoId(videoId);
        accessLog.setUserId(video.getOwnerId());
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
    public void saveFee(FileAccessLog fileAccessLog, HttpServletRequest request, String videoId,
                        String clientId, String sessionId, String resolution, String fileId) {
        OssAccessFee accessFee = ossAccessFeeService.create(
                fileAccessLog, request, videoId, clientId, sessionId, resolution, fileId);
        mongoTemplate.save(accessFee);
        log.debug("保存OSS访问费用 " + JSON.toJSONString(accessFee));
    }
}
