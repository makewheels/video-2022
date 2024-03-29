package com.github.makewheels.video2022.oss.inventory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * 每日生成OSS快照
 */
@Component
@Slf4j
public class GenerateInventoryTask {
    @Resource
    private OssInventoryService ossInventoryService;

    /**
     * 每天凌晨2点生成，当天接近零点时刻快照
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateInventory() {
        ossInventoryService.generateAndSaveInventory(LocalDate.now());
    }
}
