package com.github.makewheels.video2022.basebean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 常用字段，用于继承
 */
@Getter
@Setter
public class BaseCommonFields {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    public BaseCommonFields() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
