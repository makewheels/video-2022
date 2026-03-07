package com.github.makewheels.video2022.finance.bill;

import com.github.makewheels.video2022.basebean.BaseCommonFields;
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
public class Bill extends BaseCommonFields {
    private BigDecimal originChargePrice;   // 原始应扣金额
    private BigDecimal roundDownPrice;      // 抹零金额
    private BigDecimal realChargePrice;     // 应付金额
    @Indexed
    private Date chargeTime;                // 扣费时间

    @Indexed
    private String walletId;                // 用户钱包id
    @Indexed
    private String transactionId;           // 钱包流水id

    private Integer feeCount;               // 费用记录数量
    private String feeType;                 // 账单计费类型
    private String feeTypeName;
    private String billStatus;              // 账单状态

    public Bill() {
        this.billStatus = BillStatus.CREATED;
    }
}
