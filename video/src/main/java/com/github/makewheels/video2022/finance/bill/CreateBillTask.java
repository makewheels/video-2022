package com.github.makewheels.video2022.finance.bill;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFeeService;
import com.github.makewheels.video2022.finance.transaction.TransactionService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 自动生成账单任务
 */
@Component
@Slf4j
public class CreateBillTask {
    @Resource
    private OssAccessFeeService ossAccessFeeService;
    @Resource
    private TransactionService transactionService;
    @Resource
    private EnvironmentService environmentService;

    /**
     * 生成OSS访问账单
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void createAccessFeeBill() {
        Date billTimeStart;
        Date billTimeEnd;
        if (environmentService.isDevelopmentEnv()) {
            billTimeStart = DateUtil.beginOfDay(new Date());
            billTimeEnd = DateUtil.beginOfDay(DateUtil.tomorrow());
        } else {
            billTimeStart = DateUtil.beginOfDay(DateUtil.yesterday());
            billTimeEnd = DateUtil.beginOfDay(new Date());
        }

        log.info("开始生成OSS访问账单 billTimeStart: {} billTimeEnd: {}", billTimeStart, billTimeEnd);
        List<Bill> bills = ossAccessFeeService.createBill(billTimeStart, billTimeEnd);
        log.info("生成OSS访问账单完成 bills数量 = " + bills.size());

        log.info("开始生成交易 bills " + JSON.toJSONString(bills));
        transactionService.createTransaction(Lists.transform(bills, Bill::getId));
        log.info("生成交易完成");
    }
}
