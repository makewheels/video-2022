package com.github.makewheels.video2022.charge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private String userId;       // 用户id

    private String billStatus;   // 账单状态
    private String payStatus;    // 支付状态
    private String payChannel;   // 支付渠道
    @Indexed
    private String payId;        // 支付id

}
