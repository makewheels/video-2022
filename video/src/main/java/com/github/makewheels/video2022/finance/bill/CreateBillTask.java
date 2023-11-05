package com.github.makewheels.video2022.finance.bill;

import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 自动生成账单任务
 */
@Component
@Slf4j
public class CreateBillTask {
    @Resource
    private OssAccessFeeService ossAccessFeeService;

    /**
     * 生成OSS访问账单
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void createAccessFeeBill() {
//        Date billTimeStart = DateUtil.beginOfDay(DateUtil.yesterday());
//        Date billTimeEnd = DateUtil.beginOfDay(new Date());
        Date billTimeStart = DateUtil.beginOfDay(new Date());
        Date billTimeEnd = DateUtil.beginOfDay(DateUtil.tomorrow());
        log.info("开始生成OSS访问账单 billTimeStart: {} billTimeEnd: {}", billTimeStart, billTimeEnd);
        ossAccessFeeService.createBill(billTimeStart, billTimeEnd);
        log.info("生成OSS访问账单完成");
    }
}
