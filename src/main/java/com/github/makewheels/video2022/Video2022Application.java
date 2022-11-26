package com.github.makewheels.video2022;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
public class Video2022Application {
    @Value("${spring.profiles.active}")
    private String env;

    public static void main(String[] args) {
        File file = new File(Video2022Application.class.getResource("/").getFile(),
                "application.properties");
        FileUtil.writeUtf8String(IdUtil.objectId(), file);

        SpringApplication.run(Video2022Application.class, args);
    }

}
