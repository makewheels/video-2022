package com.github.makewheels.video2022.finance.wallet;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class WalletRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public Wallet getById(String id) {
        return mongoTemplate.findById(id, Wallet.class);
    }

    public Wallet getByUserId(String userId) {
        return mongoTemplate.findOne(Query.query(
                Criteria.where("userId").is(userId)),
                Wallet.class
        );
    }

}
