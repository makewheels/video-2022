package com.github.makewheels.video2022.finance.bill;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 账单，应付，明细项
 */
@Data
@Document
public class BillItem {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    @Indexed
    private String billId;           // 主表，账单id
    @Indexed
    private String businessId;       // 计费项关联业务表id，例如：transcodeId，OSS存储空间id，OSS请求记录id
    private String billItemStatus;   // 账单明细状态，例如：已创建，已计费
    @Indexed
    private Date chargeTime;         // 计费时间

    private String chargeItem;       // 计费项，例如：视频转码，OSS存储空间，请求流量
    private String chargeItemName;

    private String chargeUnitName;   // 计费单位，例如：秒，GB，次

    // 最终计费金额 = 单价 * 计费数量
    private BigDecimal unitPrice;    // 单价
    private BigDecimal amount;       // 计费数量，例如：视频转码时长，OSS存储空间大小，请求流量大小
    private BigDecimal billItemPrice; // 最终计费金额

    public BillItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
