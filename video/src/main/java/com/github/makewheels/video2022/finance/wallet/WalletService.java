package com.github.makewheels.video2022.finance.wallet;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 钱包服务
 */
@Service
public class WalletService {
    @Resource
    private MongoTemplate mongoTemplate;

    public Wallet createAndSaveWallet(String userId) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        mongoTemplate.save(wallet);
        return wallet;
    }
}
