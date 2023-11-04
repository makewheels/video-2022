package com.github.makewheels.video2022.finance.charge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 扣费，实付
 */
@Data
@Document
public class Charge {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    private BigDecimal shouldChargePrice;   // 应扣金额
    private BigDecimal roundDownPrice;      // 抹零金额
    private BigDecimal realChargePrice;     // 实扣金额
    @Indexed
    private Date chargeTime;                // 支付时间

    @Indexed
    private String walletId;                // 用户钱包id
    @Indexed
    private String transactionId;           // 钱包流水id

    public Charge() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
