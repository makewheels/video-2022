package com.github.makewheels.video2022.transcode.factory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QueryJobListResponseBody;
import com.aliyun.mts20140618.models.SubmitJobsResponseBody;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.Video;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 阿里云MPS转码实现类
 */
@Service
@Slf4j
public class AliyunMpsTranscodeImpl implements TranscodeService {
    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private TranscodeRepository transcodeRepository;

    @Resource
    private CacheService cacheService;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;

    /**
     * 处理回调
     */
    private void handleCallback(Transcode transcode) {
        String jobId = transcode.getJobId();

        QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                = aliyunMpsService.queryTranscodeJob(jobId).getBody().getJobList().getJob().get(0);
        String jobStatus = job.getState();
        transcode.setFinishTime(DateUtil.parseUTC(job.getFinishTime()));

        //更新转码状态到数据库
        if (!StringUtils.equals(jobStatus, transcode.getStatus())) {
            transcode.setStatus(jobStatus);
            transcode.setResult(JSONObject.parseObject(JSON.toJSONString(job)));
            cacheService.updateTranscode(transcode);
            //通知视频转码完成
            transcodeCallbackService.onTranscodeFinish(transcode);
        }
    }

    /**
     * 轮询查询阿里云转码状态
     */
    private void iterateQueryAliyunTranscodeJob(Video video, Transcode transcode) {
        String jobId = transcode.getJobId();
        long duration = video.getDuration();
        long startTime = System.currentTimeMillis();

        //轮询
        for (int i = 0; i < 1000000000; i++) {
            if (i % 3 == 0) {
                log.debug("i = " + i + " 开始睡觉");
            }
            ThreadUtil.sleep(2000);

            //如果花了视频的15倍时长都没转完，就跳出
            if ((System.currentTimeMillis() - startTime) > 15L * duration) {
                log.error("花了视频的15倍时长都没转完，来人看看这是啥 jobId = {}, video = {}",
                        jobId, JSON.toJSONString(video));
                log.error("transcode = " + JSON.toJSONString(transcode));
                break;
            }

            //查询任务
            QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                    = aliyunMpsService.queryTranscodeJob(jobId).getBody().getJobList().getJob().get(0);
            String jobStatus = job.getState();

            //只输出部分日志
            if (i % 3 == 0) {
                log.debug("阿里云轮询查询job结果: jobStatus = {}, job = {}", jobStatus,
                        JSON.toJSONString(job));
            }

            //如果转码已完成，回调
            if (AliyunTranscodeStatus.isFinishedStatus(jobStatus)) {
                callback(jobId);
                break;
            }
        }
    }

    /**
     * 发起转码
     */
    @Override
    public Transcode transcode(Video video, Transcode transcode) {
        String sourceKey = transcode.getSourceKey();
        String m3u8Key = transcode.getM3u8Key();
        String resolution = transcode.getResolution();

        //向MPS发起转码
        SubmitJobsResponseBody.SubmitJobsResponseBodyJobResultListJobResultJob job
                = aliyunMpsService.submitTranscodeJobByResolution(sourceKey, m3u8Key, resolution)
                .getBody().getJobResultList().getJobResult().get(0).getJob();
        String jobId = job.getJobId();
        log.info("发起阿里云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));

        //更新状态
        transcode.setJobId(jobId);
        transcode.setStatus(job.getState());
        cacheService.updateTranscode(transcode);

        //异步轮询查询阿里云转码状态
        new Thread(() -> iterateQueryAliyunTranscodeJob(video, transcode)).start();

        //返回
        return transcode;
    }

    /**
     * 拿到对象，发起阿里云MPS最终回调
     */
    @Override
    public void callback(String jobId) {
        log.info("阿里云MPS转码回调开始：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        Video video = cacheService.getVideo(transcode.getVideoId());
        handleCallback(transcode);
    }

}
