package com.github.makewheels.video2022.file.bean;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class TsFile extends FileBasicInfo {
    //ts视频碎片所属于哪一个转码，它的父亲
    @Indexed
    private String transcodeId;

    //ts碎片，转码所属于哪个分辨率
    private String resolution;

    //ts碎片，在一个m3u8转码文件中的位置
    private Integer tsIndex;

    //ts碎片，视频码率
    private Integer bitrate;

}
