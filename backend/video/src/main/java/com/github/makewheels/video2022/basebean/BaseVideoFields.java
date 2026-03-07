package com.github.makewheels.video2022.basebean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * 常用字段：videoId
 */
@Getter
@Setter
public class BaseVideoFields extends BaseCommonFields{
    @Indexed
    private String videoId;
}
