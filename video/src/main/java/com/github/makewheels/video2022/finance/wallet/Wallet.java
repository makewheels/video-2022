package com.github.makewheels.video2022.finance.wallet;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户钱包
 */
@Data
@Document
public class Wallet {
    @Id
    private String id;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    @Indexed
    private String userId;

    private BigDecimal balance;   // 余额

    public Wallet() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.balance = BigDecimal.ZERO;
    }

    public String toString() {
        return JSON.toJSONString(this);
    }
}
