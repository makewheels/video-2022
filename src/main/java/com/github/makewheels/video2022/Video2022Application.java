package com.github.makewheels.video2022;

import com.github.makewheels.video2022.utils.PasswordUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Video2022Application {

    public static void main(String[] args) {
        PasswordUtil.load();

        SpringApplication.run(Video2022Application.class, args);
    }

}
