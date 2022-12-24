package com.github.makewheels.video2022.bill;

import com.github.makewheels.video2022.fileaccesslog.FileAccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class BillService {
    @Resource
    private FileAccessLogService fileAccessLogService;

    public void countHourOSS(String videoId) {
        //统计访问次数和流量

    }

}
