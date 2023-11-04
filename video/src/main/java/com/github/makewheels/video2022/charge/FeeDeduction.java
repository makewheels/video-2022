package com.github.makewheels.video2022.charge;

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
public class FeeDeduction {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;

    @Indexed
    private String userId;

    private String payMethod;      // 支付方式，例如：账户余额，资源包
    private String payMethodName;
    private BigDecimal totalPayPrice;   // 支付金额
    private Date payTime;          // 支付时间

}
