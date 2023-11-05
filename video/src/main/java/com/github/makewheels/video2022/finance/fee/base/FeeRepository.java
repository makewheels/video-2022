package com.github.makewheels.video2022.finance.fee.base;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Repository
public class FeeRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 查计费：直接扣费类型
     * 例如：访问OSS文件，视频转码
     */
    public <T> List<T> listDirectDeductionFee(
            Class<T> clazz, Date billTimeStart, Date billTimeEnd, String userId,
            String feeStatus) {
        return mongoTemplate.find(Query.query(
                        Criteria.where("billTime").gte(billTimeStart).lt(billTimeEnd)
                                .and("userId").is(userId)
                                .and("feeStatus").is(feeStatus)),
                clazz
        );
    }

    /**
     * 查计费：时间范围单价类型
     * 例如：OSS存储空间
     */
    public <T> List<T> listTimeRangeFee(
            Class<T> clazz, Date billTimeStart, Date billTimeEnd, String userId) {
        return mongoTemplate.find(Query.query(
                        Criteria.where("billTimeStart").gte(billTimeStart)
                                .and("billTimeEnd").lte(billTimeEnd)
                                .and("userId").is(userId)),
                clazz
        );
    }

    /**
     * 反向关联：计费的账单id
     */
    public void updateBillId(Class<?> clazz, String feeId, String billId) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(feeId)),
                new Update().set("billId", billId).set("updateTime", new Date()),
                clazz
        );
    }

    /**
     * 反向关联：计费的账单id
     */
    public void updateStatus(Class<?> clazz, String feeId, String status) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(feeId)),
                new Update().set("status", status).set("updateTime", new Date()),
                clazz
        );
    }
}
