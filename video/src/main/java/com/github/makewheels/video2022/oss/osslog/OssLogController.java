package com.github.makewheels.video2022.oss.osslog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import javax.annotation.Resource;

@RestController
@RequestMapping("oss-log")
public class OssLogController {
    @Resource
    private OssLogService ossLogService;

    /**
     * 根据时间生成OSS访问日志
     */
    @GetMapping("generateOssAccessLog")
    public void generateOssAccessLog(@RequestParam("startDate") LocalDate startDate,
                                     @RequestParam("endDate") LocalDate endDate) {
        LocalDate currentDate = startDate;
        while (currentDate.isBefore(endDate)) {
            ossLogService.generateOssAccessLog(currentDate);
            currentDate = currentDate.plusDays(1);
        }
    }

}
