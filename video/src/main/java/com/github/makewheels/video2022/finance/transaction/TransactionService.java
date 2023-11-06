package com.github.makewheels.video2022.finance.transaction;

import com.github.makewheels.video2022.finance.bill.Bill;
import com.github.makewheels.video2022.finance.bill.BillRepository;
import com.github.makewheels.video2022.finance.bill.BillStatus;
import com.github.makewheels.video2022.finance.wallet.Wallet;
import com.github.makewheels.video2022.finance.wallet.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 交易服务
 */
@Service
@Slf4j
public class TransactionService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private WalletService walletService;
    @Resource
    private BillRepository billRepository;

    public void createTransaction(List<String> billIds) {
        List<Bill> bills = billRepository.listByIds(billIds);
        if (CollectionUtils.isEmpty(bills)) {
            log.info("billIds为空，生成交易跳过");
            return;
        }
        Transaction transaction = new Transaction();
        for (Bill bill : bills) {
            String userId = bill.getUserId();
            transaction.setUserId(userId);
            Wallet wallet = walletService.getByUserId(userId);
            transaction.setWalletId(wallet.getId());

            transaction.setAmount(bill.getRealChargePrice());
            walletService.changeBalance(wallet.getId(), bill.getRealChargePrice().negate());
            wallet = walletService.getByUserId(userId);
            transaction.setBalance(wallet.getBalance());

            transaction.setTransactionType(TransactionType.CONSUMPTION);
            transaction.setTransactionFlow(TransactionFlow.EXPENSE);
            transaction.setTransactionStatus(TransactionStatus.PAID);
            transaction.setSourceId(bill.getId());
            transaction.setTransactionTime(new Date());

            mongoTemplate.save(transaction);

            // 反向更新账单
            billRepository.updateTransactionId(billIds, transaction.getId());
            billRepository.updateBillStatus(billIds, BillStatus.CHARGED);
        }
    }
}
