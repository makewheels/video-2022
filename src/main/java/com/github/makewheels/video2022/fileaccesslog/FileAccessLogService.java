package com.github.makewheels.video2022.fileaccesslog;

import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.transcode.Transcode;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.video.VideoRepository;
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
    private VideoRepository videoRepository;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private TranscodeRepository transcodeRepository;

    public void saveAccessLog(
            HttpServletRequest request, String videoId, String clientId, String resolution, String fileId) {
        File file = fileRepository.getById(fileId);
        Transcode transcode = transcodeRepository.getById(file.getTranscodeId());

        FileAccessLog log = new FileAccessLog();
        BeanUtils.copyProperties(file, log);
        log.setFileId(file.getId());
        log.setFileType(file.getType());
        log.setTranscodeId(transcode.getId());
        log.setResolution(transcode.getResolution());
        log.setCreateTime(new Date());
        log.setIp(request.getRemoteAddr());

        mongoTemplate.save(log);
    }
}
