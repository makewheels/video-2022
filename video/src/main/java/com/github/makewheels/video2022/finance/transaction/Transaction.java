package com.github.makewheels.video2022.finance.transaction;

import com.github.makewheels.video2022.basebean.BaseCommonFields;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易表，钱包流水
 * <a href="https://error-center.aliyun.com/api/BssOpenApi/2017-12-14/QueryAccountTransactions?tab=DOC&params=">查询用户账户流水信息</a>
 */
@Getter
@Setter
@Document
public class Transaction extends BaseCommonFields {
    @Indexed
    private String walletId;        // 钱包id
    private BigDecimal amount;      // 交易金额
    private BigDecimal balance;     // 本次交易后余额

    private String transactionType; // 交易类型，例如：充值，扣费
    private String transactionFlow; // 收支类型。收入：Income。支出：Expense
    private String transactionStatus; // 交易状态
    @Indexed
    private String sourceId;        // 来源id，例如：充值id，扣费billId
    @Indexed
    private Date transactionTime;   // 交易时间

    private String remark;          // 交易备注

}
