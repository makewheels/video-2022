package com.github.makewheels.video2022.transcode;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.service.VideoReadyService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 处理转码回调
 */
@Service
@Slf4j
public class TranscodeCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private FileService fileService;
    @Resource
    private VideoReadyService videoReadyService;

    @Resource
    private IdService idService;

    /**
     * 当有一个转码job完成时回调
     */
    public void onTranscodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();

        Video video = videoRepository.getById(videoId);
        if (video == null) return;

        //更新video状态
        updateVideoStatus(video);

        //保存m3u8文件
        saveM3u8File(video, transcode);

        //保存对象存储中的ts文件
        saveS3Files(video, transcode);

        // 回调视频就绪
        if (VideoStatus.READY.equals(video.getStatus())) {
            videoReadyService.onVideoReady(video.getId());
        }
    }

    /**
     * 更新视频转码状态
     */
    private void updateVideoStatus(Video video) {
        //从数据库中查出，该视频对应的所有转码任务
        List<Transcode> transcodeList = transcodeRepository.getByIds(video.getTranscodeIds());
        //统计已完成数量
        long completeCount = transcodeList.stream().filter(Transcode::isFinishStatus).count();

        String videoStatus;
        //如果是部分完成
        if (completeCount > 0 && completeCount < transcodeList.size()) {
            videoStatus = VideoStatus.TRANSCODING_PARTLY_COMPLETE;
        } else if (completeCount == transcodeList.size()) {
            //如果全部完成
            videoStatus = VideoStatus.READY;
        } else {
            //如果一个都没完成
            videoStatus = VideoStatus.TRANSCODING;
        }

        //更新videoStatus
        if (!StringUtils.equals(videoStatus, video.getStatus())) {
            video.setStatus(videoStatus);
            videoRepository.updateStatus(video.getId(), videoStatus);
        }
    }

    /**
     * 保存m3u8文件
     */
    private File saveM3u8File(Video video, Transcode transcode) {
        String m3u8Key = transcode.getM3u8Key();

        File m3u8File = new File();
        m3u8File.setId(idService.getFileId());
        m3u8File.setFileStatus(FileStatus.READY);
        m3u8File.setKey(m3u8Key);
        m3u8File.setFileType(FileType.TRANSCODE_M3U8);
        m3u8File.setVideoId(video.getId());
        m3u8File.setVideoType(video.getVideoType());
        m3u8File.setUploaderId(video.getUploaderId());

        //获取m3u8文件内容
        OSSObject object = fileService.getObject(m3u8Key);
        m3u8File.setObjectInfo(object);

        String m3u8FileUrl = fileService.generatePresignedUrl(m3u8Key, Duration.ofMinutes(10));
        String m3u8Content = HttpUtil.get(m3u8FileUrl);
        transcode.setM3u8Content(m3u8Content);

        Assert.notNull(m3u8File.getId(), "m3u8File id is null");
        log.info("保存m3u8File: " + JSON.toJSONString(m3u8File));
        mongoTemplate.save(transcode);

        return m3u8File;
    }

    /**
     * 计算码率
     *
     * @param filesize   文件大小
     * @param timeLength 视频时长
     * @return bits per second 视频的一秒时长，有多少位，不是kbps，也不是bytes
     */
    private int calculateBitrate(long filesize, BigDecimal timeLength) {
        BigDecimal bitrate = new BigDecimal(filesize * 8)
                .divide(timeLength, RoundingMode.HALF_UP);
        return Integer.parseInt(bitrate.toString());
    }

    /**
     * 生成阿里云对象存储中的ts文件
     */
    private List<TsFile> createTsFiles(Video video, Transcode transcode) {
        String m3u8Content = transcode.getM3u8Content();

        //获取对象存储每一个文件
        String transcodeFolder = FilenameUtils.getPath(transcode.getM3u8Key());
        List<OSSObjectSummary> objects = fileService.findObjects(transcodeFolder);
        Map<String, OSSObjectSummary> ossFilenameMap = objects.stream().collect(
                Collectors.toMap(e -> FilenameUtils.getName(e.getKey()), Function.identity()));

        //获取所有ts碎片文件名
        List<String> filenames = M3u8Util.getFilenames(m3u8Content);
        //获取ts时长
        Map<String, BigDecimal> tsTimeLengthMap = M3u8Util.getTsTimeLengthMap(m3u8Content);

        //遍历每一个ts文件
        List<TsFile> tsFiles = new ArrayList<>(filenames.size());
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);
            TsFile tsFile = new TsFile();
            tsFile.setId(idService.getTsFileId());
            tsFile.setFileStatus(FileStatus.READY);

            tsFile.setFileType(FileType.TRANSCODE_TS);
            tsFile.setUploaderId(video.getUploaderId());
            tsFile.setVideoId(video.getId());
            tsFile.setVideoType(video.getVideoType());
            tsFile.setTranscodeId(transcode.getId());
            tsFile.setResolution(transcode.getResolution());
            tsFile.setTsIndex(i);
            tsFile.setObjectInfo(ossFilenameMap.get(filename));

            //计算ts码率
            Long size = tsFile.getSize();
            BigDecimal timeLength = tsTimeLengthMap.get(filename);
            tsFile.setBitrate(calculateBitrate(size, timeLength));

            tsFiles.add(tsFile);
        }
        return tsFiles;
    }

    /**
     * 转码完成后，更新对象存储ts碎片
     */
    private List<TsFile> saveS3Files(Video video, Transcode transcode) {
        List<TsFile> tsFiles = createTsFiles(video, transcode);

        //保存所有ts文件到数据库
        log.info("保存tsFiles, 总共 {} 个", tsFiles.size());
        mongoTemplate.insertAll(tsFiles);

        //反向更新transcode的ts文件id列表
        transcode.setTsFileIds(Lists.transform(tsFiles, TsFile::getId));

        //计算平均码率
        long tsTotalSize = tsFiles.stream().mapToLong(TsFile::getSize).sum();
        BigDecimal duration = new BigDecimal(video.getMediaInfo().getDuration() / 1000);
        transcode.setAverageBitrate(calculateBitrate(tsTotalSize, duration));

        //计算最高码率
        Integer maxBitrate = tsFiles.stream()
                .max(Comparator.comparing(TsFile::getBitrate)).get().getBitrate();
        transcode.setMaxBitrate(maxBitrate);

        mongoTemplate.save(transcode);
        return tsFiles;
    }

}
