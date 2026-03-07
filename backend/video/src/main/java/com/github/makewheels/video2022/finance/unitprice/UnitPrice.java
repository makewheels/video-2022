package com.github.makewheels.video2022.finance.unitprice;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单价
 */
@Data
public class UnitPrice {
    private BigDecimal unitPrice;    // 单价

    private String unitName;         // 计费单位，例如：秒，GB，次
    private String priceDesc;        // 价格描述
}
