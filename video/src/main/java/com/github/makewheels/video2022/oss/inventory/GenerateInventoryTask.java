package com.github.makewheels.video2022.oss.inventory;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.oss.inventory.bean.OssInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 每日生成OSS快照
 */
@Component
@Slf4j
public class GenerateInventoryTask {
    @Resource
    private OssInventoryRepository ossInventoryRepository;
    @Resource
    private OssInventoryService ossInventoryService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void generateInventory() {
        Integer date = Integer.valueOf(DateUtil.format(new Date(), DatePattern.PURE_DATE_PATTERN));

        // 判断今天是否已经生成过快照
        if (ossInventoryRepository.isInventoryGenerationDate(date)) {
            log.info("今天已经生成过快照");
            return;
        }

        // 生成快照
        log.info("开始生成快照");
        ossInventoryService.generateInventory(LocalDate.parse(String.valueOf(date),
                DateTimeFormatter.ofPattern(DatePattern.PURE_DATE_PATTERN)));
        log.info("生成快照完成");
    }
}
