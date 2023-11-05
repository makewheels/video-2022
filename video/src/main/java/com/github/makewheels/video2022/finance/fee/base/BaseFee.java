package com.github.makewheels.video2022.finance.fee.base;

import com.github.makewheels.video2022.basebean.BaseVideoFields;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;

/**
 * 产生的费用
 */
@Getter
@Setter
public class BaseFee extends BaseVideoFields {
    @Indexed
    private String billId;           // 主表，账单id，打包生成账单后回写
    private String feeStatus;        // 费用状态，例如：已创建，已计费

    private String feeType;          // 计费项，例如：视频转码，OSS存储空间，请求流量
    private String feeTypeName;

    // 计费金额 = 单价 * 计费数量
    private String unitName;         // 计费单位，例如：秒，GB，次
    private BigDecimal unitPrice;    // 单价
    private BigDecimal amount;       // 计费数量，例如：视频转码时长，OSS存储空间大小，请求流量大小
    private BigDecimal feePrice;     // 计费金额

    public BaseFee() {
        this.feeStatus = FeeStatus.CREATED;
    }
}
