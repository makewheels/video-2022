package com.github.makewheels.video2022.ding;

import lombok.Data;

@Data
public class RobotConfig {
    private String type;   // 是哪个机器人，是video exception 还是watch log
    private String accessToken;
    private String secret;

}
