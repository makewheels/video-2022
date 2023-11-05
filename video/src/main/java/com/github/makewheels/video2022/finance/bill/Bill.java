package com.github.makewheels.video2022.finance.bill;

import com.github.makewheels.video2022.basebean.BaseVideoFields;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 打包产生的费用，扣费
 */
@Getter
@Setter
@Document
public class Bill extends BaseVideoFields {
    private BigDecimal shouldChargePrice;   // 应扣金额
    private BigDecimal roundDownPrice;      // 抹零金额
    private BigDecimal realChargePrice;     // 实扣金额
    @Indexed
    private Date chargeTime;                // 扣费时间

    @Indexed
    private String walletId;                // 用户钱包id
    @Indexed
    private String transactionId;           // 钱包流水id

}
