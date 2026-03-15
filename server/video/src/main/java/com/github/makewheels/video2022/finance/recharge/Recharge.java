package com.github.makewheels.video2022.finance.recharge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 充值
 */
@Data
@Document
public class Recharge {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    @Indexed
    private String walletId;          // 钱包id
    private BigDecimal chargeAmount;  // 充值金额
    private String rechargeStatus;    // 充值状态，例如：已创建，已支付
    @Indexed
    private String transactionId;     // 钱包流水id

    private String payMethod;         // 支付方式，例如：微信，支付宝
    @Indexed
    private String outTradeNo;        // 第三方支付订单号

    public Recharge() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.rechargeStatus = RechargeStatus.CREATED;
    }
}
