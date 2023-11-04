package com.github.makewheels.video2022.finance.bill;

import com.github.makewheels.video2022.finance.charge.ChargeStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 账单，应付，汇总
 */
@Data
@Document
public class Bill {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    private String billStatus;            // 账单状态，例如：已创建，已支付
    private String chargeStatus;          // 扣费状态，例如：已创建，已支付
    private BigDecimal billPrice;    // 计费金额

    @Indexed
    private String feeDeductionId; // 实付扣费id

    public Bill() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.billStatus = BillStatus.CREATED;
        this.chargeStatus = ChargeStatus.CREATED;
    }

}
