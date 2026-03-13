package com.github.makewheels.video2022.etc.ding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RobotFactory {
    @Value("${dingtalk.robot.watchlog.access-token:}")
    private String watchLogAccessToken;
    @Value("${dingtalk.robot.watchlog.secret:}")
    private String watchLogSecret;
    @Value("${dingtalk.robot.exception.access-token:}")
    private String exceptionAccessToken;
    @Value("${dingtalk.robot.exception.secret:}")
    private String exceptionSecret;

    public RobotConfig getRobotByType(String robotType) {
        if (robotType.equals(RobotType.WATCH_LOG)) {
            return getWatchLog();
        } else if (robotType.equals(RobotType.EXCEPTION)) {
            return getVideoException();
        }
        return null;
    }

    public RobotConfig getWatchLog() {
        RobotConfig robotConfig = new RobotConfig();
        robotConfig.setType(RobotType.WATCH_LOG);
        robotConfig.setAccessToken(watchLogAccessToken);
        robotConfig.setSecret(watchLogSecret);
        return robotConfig;
    }

    public RobotConfig getVideoException() {
        RobotConfig robotConfig = new RobotConfig();
        robotConfig.setType(RobotType.EXCEPTION);
        robotConfig.setAccessToken(exceptionAccessToken);
        robotConfig.setSecret(exceptionSecret);
        return robotConfig;
    }
}
