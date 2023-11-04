package com.github.makewheels.video2022.charge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 账单，应付
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
    private String userId;         // 用户id

    private String billStatus;     // 账单状态，例如：已创建，已支付
    private String payStatus;      // 支付状态，例如：已创建，已支付
    private String payMethod;      // 支付方式，例如：账户余额，资源包
    private BigDecimal totalChargePrice;  // 计费金额

    @Indexed
    private String feeDeductionId; // 实付扣费id

}
