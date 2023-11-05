package com.github.makewheels.video2022.finance.unitprice;

import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 单价计算
 */
@Service
@Slf4j
public class UnitPriceService {
    public static final int SCALE = 20;

    /**
     * 获取OSS文件访问单价
     * <a href="https://www.aliyun.com/price/product#/oss/detail/ossbag">OSS定价</a>
     * 00:00-08:00（闲时）：0.25元/GB
     * 08:00-24:00（忙时）：0.50元/GB
     *
     * @param accessTime 访问时间
     */
    public UnitPrice getOssAccessUnitPrice(Date accessTime) {
        int hour = DateUtil.hour(accessTime, true);
        BigDecimal pricePerGb;
        if (hour >= 0 && hour <= 7) {
            pricePerGb = new BigDecimal("0.25");
        } else {
            pricePerGb = new BigDecimal("0.50");
        }
        BigDecimal oneGigaBytes = new BigDecimal("1024").pow(3);
        UnitPrice unitPrice = new UnitPrice();
        unitPrice.setUnitPrice(
                pricePerGb.divide(oneGigaBytes, SCALE, RoundingMode.HALF_DOWN)
        );
        return unitPrice;
    }
}
