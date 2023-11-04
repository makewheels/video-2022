package com.github.makewheels.video2022.charge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 扣费实付item
 */
@Data
@Document
public class FeeDeductionItem {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;

    @Indexed
    private String userId;
    @Indexed
    private String feeDeductionId;  // 扣费主表id
    @Indexed
    private String billId;          // 账单id
    private BigDecimal payPrice;    // 支付金额

}
