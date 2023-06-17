package com.github.makewheels.video2022.video.bean.entity;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.constants.ObjectStorageProvider;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * 视频对象
 */
@Data
@Document
public class Video {
    @Id
    private String id;

    @Indexed
    private String uploaderId;   // 上传者
    @Indexed
    private String ownerId;      // 所有者

    // 基本信息
    private String title;
    private String description;

    @Indexed
    private String type;        // 类型：是用户上传还是YouTube
    @Indexed
    private String provider;    // 它就是对象存储提供商，和file是一对一关系
    @Indexed
    private String status;      // 转码状态

    // 关联id
    private List<String> transcodeIds;  // 转码id列表
    @Indexed
    private String rawFileId; // 用户上传原始视频文件id
    @Indexed
    private String coverId;  // 封面id

    // 子类
    private YouTube youTube;               // YouTube视频信息
    private MediaInfo mediaInfo;           // 媒体信息
    private StorageStatus storageStatus;   // 删除信息
    private Watch watch;                   // 播放信息
    private Link link;                     // 视频链接

    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;

    public Video() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.status = VideoStatus.CREATED;
        this.provider = ObjectStorageProvider.ALIYUN_OSS;

        this.youTube = new YouTube();
        this.mediaInfo = new MediaInfo();
        this.storageStatus = new StorageStatus();
        this.watch = new Watch();
        this.link = new Link();
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public boolean isYoutube() {
        return VideoType.YOUTUBE.equals(this.type);
    }

    public boolean isReady() {
        return VideoStatus.READY.equals(this.status);
    }
}
