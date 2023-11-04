package com.github.makewheels.video2022.finance.transaction;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 交易表，钱包流水
 */
@Data
@Document
public class Transaction {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    @Indexed
    private String walletId;    // 钱包id
    private String type;        // 流水类型，例如：充值，扣费

    @Indexed
    private String sourceId;    // 来源id，例如：充值id，扣费id
    @Indexed
    private Date transactionTime; // 交易时间

    public Transaction() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
