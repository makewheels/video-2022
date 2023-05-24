package com.github.makewheels.video2022.etc.ding;

import org.springframework.stereotype.Service;

@Service
public class RobotFactory {
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
        robotConfig.setAccessToken("960a84c7fac8bb09ac013bd0ed23b7085282d5dc59aeb3c08562c1fb3e961699");
        robotConfig.setSecret("SEC924dc9d2ba24b5f5354f674adc81d33626ed997e1823de89f04338e581cebb1c");
        return robotConfig;
    }

    public RobotConfig getVideoException() {
        RobotConfig robotConfig = new RobotConfig();
        robotConfig.setType(RobotType.EXCEPTION);
        robotConfig.setAccessToken("99ce152711f0c3efa33f83a4e7b3a465512145bec44b43b9a6f9e5887c1e0273");
        robotConfig.setSecret("SECd9cd14eb5b18ba000a87a0a5ff1ff54ad0acd850d08ff75690e83df6a8122433");
        return robotConfig;
    }
}
