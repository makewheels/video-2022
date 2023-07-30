package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSON;
import com.aliyun.mts20140618.models.QuerySnapshotJobListResponseBody;
import com.aliyun.oss.model.CannedAccessControlList;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class CoverCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private CoverRepository coverRepository;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private FileService fileService;

    public Result<Void> youtubeUploadFinishCallback(String coverId) {
        Cover cover = mongoTemplate.findById(coverId, Cover.class);
        if (cover == null) {
            return Result.error(ErrorCode.COVER_NOT_EXIST);
        }
        String key = cover.getKey();
        //向对象存储确认文件存在
        String coverProvider = cover.getProvider();
        if (StringUtils.equalsAny(coverProvider, CoverProvider.ALIYUN_CLOUD_FUNCTION, CoverProvider.ALIYUN_MPS)) {
            if (!fileService.doesOSSObjectExist(key)) {
                return Result.error(ErrorCode.FILE_NOT_EXIST);
            }
        }
        //更新cover状态
        cover.setStatus(CoverStatus.READY);
        cover.setFinishTime(new Date());
        mongoTemplate.save(cover);
        log.info("成功处理搬运youtube封面回调，cover = {}", JSON.toJSONString(cover));
        return Result.ok();
    }

    /**
     * 阿里云轮询查截帧任务是否完成
     */
    public void iterateQueryAliyunSnapshotJob(Video video, Cover cover) {
        String jobId = cover.getJobId();
        long startTime = System.currentTimeMillis();
        //轮询
        for (int i = 0; i < 1000000000; i++) {
            //查询任务
            QuerySnapshotJobListResponseBody.QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob
                    job = aliyunMpsService.simpleQueryOneJob(jobId);
            String jobStatus = job.getState();
            log.info("阿里云轮询查询job结果: jobStatus = {}, job = {}", jobStatus, JSON.toJSONString(job));
            //如果转码已完成，回调
            if (jobStatus.equals("Success")) {
                aliyunCoverCallback(jobId);
                break;
            }

            log.info("i = " + i + " 开始睡觉");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if ((System.currentTimeMillis() - startTime) > 3 * 60 * 1000) {
                log.error("视频截帧长时间未完成: jobId = {}, video = {}", jobId, JSON.toJSONString(video));
                log.error("cover = " + JSON.toJSONString(cover));
                break;
            }
        }
    }

    /**
     * 当阿里云MPS截帧完成时回调
     */
    private void aliyunCoverCallback(String jobId) {
        log.info("阿里云截帧完成: jobId = {}", jobId);
        //根据jobId找到这个截帧任务
        Cover cover = coverRepository.getByJobId(jobId);

        //再向阿里云查一次截帧任务
        QuerySnapshotJobListResponseBody.QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob
                job = aliyunMpsService.simpleQueryOneJob(jobId);
        String jobStatus = job.getState();

        //更新cover
        cover.setStatus(jobStatus);
        cover.setFinishTime(new Date());
        cover.setResult(JSON.parseObject(JSON.toJSONString(job)));
        mongoTemplate.save(cover);

        //更新file
        File file = fileRepository.getById(cover.getFileId());
        String key = file.getKey();

        //更改封面的OSS权限为公开读
        file.setAcl(CannedAccessControlList.PublicRead.toString());
        fileService.changeObjectAcl(file.getId(), CannedAccessControlList.PublicRead.toString());

        file.setObjectInfo(fileService.getObject(key));
        file.setFileStatus(FileStatus.READY);
        mongoTemplate.save(file);
    }
}
