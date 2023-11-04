package com.github.makewheels.video2022.charge;

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
    private String billId;           // 主表，账单id
    @Indexed
    private String userId;

    @Indexed
    private String associateId;      // 关联计费项id，例如：转码id，OSS存储空间id，请求记录id

    private String chargeItem;       // 计费项，例如：视频转码，OSS存储空间，请求流量
    private String chargeItemName;

    private String chargeUnitName;   // 计费单位，例如：秒，GB，次

    // 最终计费金额 = 计费数量 * 单价
    private BigDecimal chargeAmount; // 计费数量，例如：视频转码时长，OSS存储空间大小，请求流量大小
    private BigDecimal unitPrice;    // 单价
    private BigDecimal chargePrice;  // 最终计费金额

}
