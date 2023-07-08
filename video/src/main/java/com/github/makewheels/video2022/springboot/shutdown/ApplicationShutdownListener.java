package com.github.makewheels.video2022.springboot.shutdown;

import com.github.makewheels.video2022.file.oss.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class ApplicationShutdownListener implements ApplicationListener<ContextClosedEvent> {
    @Resource
    private OssService ossService;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("SpringBoot 关闭了 shutdown");
        ossService.shutdownClient();
    }
}
