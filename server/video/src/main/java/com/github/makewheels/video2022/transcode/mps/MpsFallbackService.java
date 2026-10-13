package com.github.makewheels.video2022.transcode.mps;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Lazy;
import java.util.Date;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.task.FcTask;
import com.github.makewheels.video2022.transcode.task.FcTaskRepository;
import com.github.makewheels.video2022.transcode.task.FcTaskStatus;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * MPS 有限兜底：自建重试耗尽后允许一次。媒体保留要求不满足（HDR 等）不允许兜底，直接失败。
 */
@Service
@Slf4j
public class MpsFallbackService {
    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FcTaskRepository fcTaskRepository;
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource @Lazy
    private TranscodeCallbackService callbackService;

    @Value("${transcode.recovery.enabled:true}")
    private boolean recoveryEnabled;

    @Value("${transcode.fallback-mps.enabled:true}")
    private boolean fallbackEnabled;
    @Value("${aliyun.mps.fallback-template.720p:}")
    private String fallbackTemplate720p;
    @Value("${aliyun.mps.fallback-template.1080p:}")
    private String fallbackTemplate1080p;

    /**
     * 是否允许 MPS 兜底：开关开启、兜底模板已配置、transcode 未兜底过、
     * 源媒体为 SDR（HDR 无法由现有 H.264 模板保留，兜底会改变媒体表现 → 不允许）。
     */
    public boolean canFallback(String videoId, String transcodeId) {
        if (!fallbackEnabled) {
            log.info("MPS 兜底开关关闭 videoId = {}", videoId);
            return false;
        }
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) return false;
        if (transcode.getFallbackCount() != null && transcode.getFallbackCount() > 0) {
            log.info("该档已兜底过，不重复兜底 transcodeId = {}", transcodeId);
            return false;
        }
        Video video = videoRepository.getById(videoId);
        if (video == null) return false;
        MediaInfo mediaInfo = video.getMediaInfo();
        if (mediaInfo == null) return false;
        String dynamicRange = mediaInfo.getDynamicRange();
        if (!"SDR".equals(dynamicRange)) {
            log.info("源为 {}，MPS 模板无法保留，不允许兜底 transcodeId = {}", dynamicRange, transcodeId);
            return false;
        }
        if (!preservableByTemplate(mediaInfo)) return false;
        String templateId = templateFor(transcode.getResolution());
        if (templateId == null || templateId.isBlank()) {
            log.warn("兜底模板未配置（aliyun.mps.fallback-template.*），不允许兜底 transcodeId = {}",
                    transcodeId);
            return false;
        }
        return true;
    }

    private boolean supportedFrameRate(String fps) {
        if (fps == null) return false;
        String[] fraction = fps.split("/");
        double rate = Double.parseDouble(fraction[0]) / (fraction.length > 1
                ? Double.parseDouble(fraction[1]) : 1);
        return rate > 0 && rate <= 60;
    }

    private boolean preservableByTemplate(MediaInfo mediaInfo) {
        if (!supportedFrameRate(mediaInfo.getFrameRate())) return false;
        if (!supportedAudio(mediaInfo)) return false;
        // 未验明的媒体情况不能交给通用模板悄悄转换。
        if ("VFR".equals(mediaInfo.getFrameRateMode())
                || (mediaInfo.getAudioTrackCount() != null && mediaInfo.getAudioTrackCount() > 1)
                || (mediaInfo.getRotation() != null && mediaInfo.getRotation() != 0)
                || (mediaInfo.getSar() != null && !"1:1".equals(mediaInfo.getSar()))
                || (mediaInfo.getBitDepth() != null && mediaInfo.getBitDepth() > 8)) return false;
        return true;
    }

    private boolean supportedAudio(MediaInfo mediaInfo) {
        Integer count = mediaInfo.getAudioTrackCount();
        if (count == null || count > 1) return false;
        if (count == 0) return true;
        JSONObject audio = mediaInfo.getAudioTracks();
        if (audio == null || audio.getJSONArray("tracks") == null || audio.getJSONArray("tracks").size() != 1) {
            return false;
        }
        Integer channels = audio.getJSONArray("tracks").getJSONObject(0).getInteger("channels");
        return java.util.Set.of(1, 2, 4, 5, 6, 8).contains(channels == null ? 0 : channels);
    }

    /**
     * 提交 MPS 兜底任务。原子抢占 fallbackCount（0 → 1）防止重复提交。
     */
    public boolean fallback(FcTask task) {
        String transcodeId = task.getRefId();
        Transcode transcode = transcodeRepository.getById(transcodeId);
        if (transcode == null) return false;

        // 原子抢占兜底名额：只有第一个 fallbackCount 0→1 的调用者真正提交
        Query query = Query.query(Criteria.where("id").is(transcodeId)
                .orOperator(Criteria.where("fallbackCount").isNull(),
                        Criteria.where("fallbackCount").is(0)));
        boolean grabbed = mongoTemplate.updateFirst(query,
                new Update().set("fallbackCount", 1), Transcode.class).getModifiedCount() == 1;
        if (!grabbed) {
            log.info("兜底名额已被占用，跳过 transcodeId = {}", transcodeId);
            return false;
        }

        String key = transcode.getM3u8Key();
        key = key.substring(0, key.lastIndexOf('/')) + "/mps/" + transcodeId + ".m3u8";
        Date deadline = new Date(System.currentTimeMillis() + 1_800_000L);
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(transcodeId)),
                new Update().set("currentProvider", TranscodeProvider.ALIYUN_MPS)
                        .set("status", "Submitted").set("m3u8Key", key)
                        .set("fallbackDeadline", deadline), Transcode.class);
        fcTaskRepository.updateCurrent(task, task.getStatus(),
                new Update().set("status", FcTaskStatus.FALLBACK_PENDING).set("deadline", deadline));
        String jobId;
        try {
            jobId = aliyunMpsService.submitFallbackTranscodeJob(
                    transcode.getSourceKey(), key, transcode.getResolution(), outputOverrides(task));
        } catch (Exception e) {
            fail(task, "MPS 提交失败，禁止重复提交");
            return true;
        }
        // 名额已消耗：响应不确定不自动再次提交 MPS。
        if (jobId == null) {
            fail(task, "MPS 提交结果不确定，禁止重复提交");
            return true;
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(transcodeId)),
                new Update().set("jobId", jobId).set("fallbackPollAfter", new Date()), Transcode.class);
        return true;
    }

    private JSONObject outputOverrides(FcTask task) {
        JSONObject policy = task.getPayloadSnapshot().getJSONObject("mediaPolicy");
        JSONObject video = new JSONObject();
        video.put("Codec", "H.264");
        video.put("Profile", "high");
        video.put("Width", policy.getString("outputWidth"));
        video.put("Height", policy.getString("outputHeight"));
        video.put("LongShortMode", "false");
        video.put("Bitrate", String.valueOf(Math.max(10, policy.getLongValue("targetBitrateBps") / 1000)));
        video.put("Gop", "6s");
        // Fps 不设置，MPS 取输入值；>60fps/VFR 源已在 eligibility 中拒绝。
        MediaInfo media = videoRepository.getById(task.getVideoId()).getMediaInfo();
        JSONObject audio = new JSONObject();
        if (media.getAudioTrackCount() == null || media.getAudioTrackCount() == 0) {
            audio.put("Remove", "true");
        } else {
            audio.put("Codec", "AAC");
            var tracks = media.getAudioTracks().getJSONArray("tracks");
            audio.put("Channels", tracks.getJSONObject(0).getString("channels"));
            audio.put("Bitrate", "128");
        }
        JSONObject overrides = new JSONObject();
        overrides.put("Video", video);
        overrides.put("Audio", audio);
        return overrides;
    }

    /** 数据库驱动轮询，应用重启后继续处理已提交的 MPS 任务。 */
    @Scheduled(fixedDelay = 30_000L)
    public void pollFallbacks() {
        if (!recoveryEnabled) return;
        Query query = Query.query(Criteria.where("status").is(FcTaskStatus.FALLBACK_PENDING));
        for (FcTask task : mongoTemplate.find(query, FcTask.class)) {
            try {
                pollOne(task);
            } catch (Exception e) {
                log.warn("MPS 轮询暂未完成 taskId = {}, error = {}", task.getId(), e.getMessage());
                if (task.getDeadline() != null && task.getDeadline().before(new Date())) {
                    fail(task, "MPS 兜底超时或产物登记失败");
                }
            }
        }
    }

    private void pollOne(FcTask task) {
        Transcode lane = transcodeRepository.getById(task.getRefId());
        if (lane == null || lane.getJobId() == null) {
            if (task.getDeadline().before(new Date())) fail(task, "MPS 任务编号未持久化");
            return;
        }
        Query lease = Query.query(Criteria.where("id").is(lane.getId())
                .orOperator(Criteria.where("fallbackPollAfter").isNull(),
                        Criteria.where("fallbackPollAfter").lte(new Date())));
        if (mongoTemplate.updateFirst(lease, new Update().set("fallbackPollAfter",
                new Date(System.currentTimeMillis() + 300_000L)), Transcode.class).getModifiedCount() != 1) return;
        try {
            var response = aliyunMpsService.queryTranscodeJob(lane.getJobId());
            if (response == null || response.getBody() == null) throw new IllegalStateException("查询返回为空");
            var job = response.getBody().getJobList().getJob().get(0);
            if (AliyunTranscodeStatus.TranscodeSuccess.equals(job.getState())) {
                JSONObject manifest = new JSONObject();
                manifest.put("playlistKey", lane.getM3u8Key());
                manifest.put("dynamicRange", "SDR");
                // 实际流信息由 MPS 返回，缺失值不伪造 CODECS 或尺寸。
                JSONObject raw = JSONObject.parseObject(com.alibaba.fastjson.JSON.toJSONString(job));
                JSONObject output = raw.getJSONObject("output");
                JSONObject properties = output == null ? null : output.getJSONObject("properties");
                validateMedia(task, properties);
                manifest.put("width", properties.getInteger("width"));
                manifest.put("height", properties.getInteger("height"));
                manifest.put("frameRate", properties.getString("fps"));
                callbackService.onSelfHostedTranscodeFinish(task, manifest);
                fcTaskRepository.finish(task.getId(), task.getAttemptId(), FcTaskStatus.SUCCEEDED, new Date(), null);
            } else if (AliyunTranscodeStatus.isFinishStatus(job.getState())) {
                fail(task, "MPS 兜底失败: " + job.getState());
            } else if (task.getDeadline().before(new Date())) {
                fail(task, "MPS 兜底超时");
            }
        } finally {
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(lane.getId())),
                    new Update().set("fallbackPollAfter", new Date()), Transcode.class);
        }
    }

    private void validateMedia(FcTask task, JSONObject properties) {
        JSONObject expected = task.getPayloadSnapshot().getJSONObject("mediaPolicy");
        if (properties == null || !expected.getInteger("outputWidth").equals(properties.getInteger("width"))
                || !expected.getInteger("outputHeight").equals(properties.getInteger("height"))) {
            throw new IllegalStateException("MPS 输出缺少媒体参数或尺寸不符合任务");
        }
        String sourceFps = videoRepository.getById(task.getVideoId()).getMediaInfo().getFrameRate();
        String[] fraction = sourceFps.split("/");
        double rate = Double.parseDouble(fraction[0]) / (fraction.length > 1
                ? Double.parseDouble(fraction[1]) : 1);
        Double actual = properties.getDouble("fps");
        if (actual == null || Math.abs(actual - rate) > 0.001) {
            throw new IllegalStateException("MPS 输出帧率不符合原片");
        }
    }

    private void fail(FcTask task, String error) {
        fcTaskRepository.finish(task.getId(), task.getAttemptId(), FcTaskStatus.FAILED, new Date(), error);
        callbackService.onFcTaskFailed(task, error);
    }

    private String templateFor(String resolution) {
        return switch (resolution) {
            case "720p" -> fallbackTemplate720p;
            case "1080p" -> fallbackTemplate1080p;
            default -> null;
        };
    }
}
