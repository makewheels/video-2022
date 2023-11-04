package com.github.makewheels.video2022.finance.charge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 扣费item，和Bill是一对一关系
 */
@Data
@Document
public class ChargeItem {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    @Indexed
    private String chargeId;  // 扣费主表id
    @Indexed
    private String billId;          // 账单id，FeeDeductionItem和Bill，是一对一关系
    private BigDecimal chargeItemPrice;    // 支付金额，对应Bill的totalChargePrice

    public ChargeItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
