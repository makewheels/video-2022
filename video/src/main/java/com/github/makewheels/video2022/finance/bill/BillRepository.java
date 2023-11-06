package com.github.makewheels.video2022.finance.bill;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Repository
public class BillRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Bill getById(String id) {
        return mongoTemplate.findById(id, Bill.class);
    }

    public List<Bill> listByIds(List<String> idList) {
        return mongoTemplate.find(Query.query(Criteria.where("id").in(idList)), Bill.class);
    }

    /**
     * 批量更新交易id
     */
    public void updateTransactionId(List<String> idList, String transactionId) {
        mongoTemplate.updateMulti(Query.query(Criteria.where("id").in(idList)),
                new Update().set("transactionId", transactionId)
                        .set("updateTime", new Date()),
                Bill.class);
    }

    /**
     * 批量更新账单状态
     */
    public void updateBillStatus(List<String> idList, String billStatus) {
        mongoTemplate.updateMulti(Query.query(Criteria.where("id").in(idList)),
                new Update().set("billStatus", billStatus)
                        .set("updateTime", new Date()),
                Bill.class);
    }
}