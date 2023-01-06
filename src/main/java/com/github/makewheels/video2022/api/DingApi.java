package com.github.makewheels.video2022.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DingApi extends Api {
    private String title;
    private String text;
    private String messageType;
}
