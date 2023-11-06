package com.github.makewheels.video2022.finance.wallet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 钱包服务
 */
@Service
@Slf4j
public class WalletService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private WalletRepository walletRepository;

    /**
     * 获取钱包
     */
    public Wallet getByUserId(String userId) {
        Wallet wallet = walletRepository.getByUserId(userId);
        if (wallet == null) {
            wallet = createWallet(userId);
        }
        return wallet;
    }

    /**
     * 创建钱包
     */
    public Wallet createWallet(String userId) {
        Wallet wallet = walletRepository.getByUserId(userId);
        if (wallet != null) {
            return wallet;
        }
        wallet = new Wallet();
        wallet.setUserId(userId);
        mongoTemplate.save(wallet);
        log.info("创建钱包 userId = {}, wallet = {}", userId, wallet);
        return wallet;
    }

    /**
     * 改余额
     */
    public void changeBalance(String walletId, BigDecimal changeAmount) {
        Wallet wallet = walletRepository.getById(walletId);
        wallet.setBalance(wallet.getBalance().add(changeAmount));
        wallet.setUpdateTime(new Date());
        mongoTemplate.save(wallet);
        log.info("改余额 userId = {}, walletId = {}, changeAmount = {}, wallet = {}",
                wallet.getUserId(), walletId, changeAmount, wallet);
    }


}
