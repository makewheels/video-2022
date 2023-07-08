package com.github.makewheels.video2022.file.access;

import com.github.makewheels.video2022.file.TsFileRepository;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Service
public class FileAccessLogService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TsFileRepository tsFileRepository;
    @Resource
    private TranscodeRepository transcodeRepository;

    /**
     * 保存文件访问记录
     */
    public void saveAccessLog(
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
    }
}
