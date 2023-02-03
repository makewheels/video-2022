package com.github.makewheels.video2022.video.bean.video;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
public class Watch {
    @Indexed
    private String watchId;
    private String watchUrl;
    private String shortUrl;
}
