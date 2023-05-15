package com.github.makewheels.video2022.video.bean.entity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.constants.S3Provider;
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
    private String userId;   // 所有者 ownerId

//    @Indexed
//    private String uploader; //上传者

    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
    private String title;
    private String description;
    private Integer watchCount; //观看次数
    private Long duration;      //视频时长，单位毫秒
    private List<String> transcodeIds; //转码id列表

    @Indexed
    private String originalFileId;

    private String coverId;

    //    private MediaInfo mediaInfo;
    private Integer width;  // TODO 字段挪到子类 MediaInfo
    private Integer height;// TODO 字段挪到子类 MediaInfo
    private String videoCodec;// TODO 字段挪到子类 MediaInfo
    private String audioCodec;// TODO 字段挪到子类 MediaInfo
    private Integer bitrate;// TODO 字段挪到子类 MediaInfo
    private JSONObject mediaInfo;// TODO 字段挪到子类 MediaInfo

    @Indexed
    private String type;  //类型：是用户上传还是YouTube
    @Indexed
    private String provider;    //它就是对象存储提供商，和file是一对一关系

    private YouTube youTube;

    @Indexed
    private String status;     //转码状态
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;

    @Indexed
    private Date expireTime;                    //过期时间
    @Indexed
    private Boolean isPermanent;                //是否是永久视频
    private Boolean isOriginalFileDeleted;      //源视频是否已删除
    private Boolean isTranscodeFilesDeleted;    //ts转码文件是否已删除
    private Date deleteTime;                    //什么时候删的

    public Video() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.status = VideoStatus.CREATED;
        this.provider = S3Provider.ALIYUN_OSS;
        this.watchCount = 0;
        this.isPermanent = false;
        this.isOriginalFileDeleted = false;
        this.isTranscodeFilesDeleted = false;

        this.youTube = new YouTube();
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