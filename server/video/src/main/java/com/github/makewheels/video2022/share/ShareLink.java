package com.github.makewheels.video2022.share;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class ShareLink {
    @Id
    private String id;

    @Indexed
    private String videoId;

    @Indexed(unique = true)
    private String shortCode;

    @Indexed
    private String createdBy;

    private long clickCount;

    private String lastReferrer;

    private Date createTime;

    public ShareLink() {
        this.createTime = new Date();
        this.clickCount = 0;
    }
}
