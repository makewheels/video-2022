package com.github.makewheels.video2022.fileaccesslog;

import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileRepository;
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
    private FileRepository fileRepository;
    @Resource
    private TranscodeRepository transcodeRepository;

    /**
     * 保存文件访问记录
     */
    public void saveAccessLog(
            HttpServletRequest request, String videoId, String clientId, String sessionId,
            String resolution, String fileId) {
        File file = fileRepository.getById(fileId);
        String transcodeId = file.getTranscodeId();
        Transcode transcode = transcodeRepository.getById(transcodeId);

        FileAccessLog log = new FileAccessLog();
        BeanUtils.copyProperties(file, log);
        log.setId(null);
        log.setFileId(file.getId());
        log.setFileType(file.getType());

        log.setClientId(clientId);
        log.setSessionId(sessionId);
        log.setTranscodeId(transcode.getId());
        log.setResolution(transcode.getResolution());
        log.setCreateTime(new Date());
        //TODO 拿不到ip
        log.setIp(request.getRemoteAddr());

        mongoTemplate.save(log);
    }
}
