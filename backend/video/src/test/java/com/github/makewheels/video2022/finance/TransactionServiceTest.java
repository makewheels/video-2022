package com.github.makewheels.video2022.finance;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.finance.bill.Bill;
import com.github.makewheels.video2022.finance.bill.BillStatus;
import com.github.makewheels.video2022.finance.transaction.Transaction;
import com.github.makewheels.video2022.finance.transaction.TransactionFlow;
import com.github.makewheels.video2022.finance.transaction.TransactionService;
import com.github.makewheels.video2022.finance.transaction.TransactionStatus;
import com.github.makewheels.video2022.finance.transaction.TransactionType;
import com.github.makewheels.video2022.finance.wallet.Wallet;
import com.github.makewheels.video2022.finance.wallet.WalletService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    private String randomUserId() {
        return new ObjectId().toHexString();
    }

    private Bill createAndSaveBill(String userId, BigDecimal realChargePrice) {
        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setRealChargePrice(realChargePrice);
        bill.setOriginChargePrice(realChargePrice);
        bill.setRoundDownPrice(BigDecimal.ZERO);
        return mongoTemplate.save(bill);
    }

    // ---- createTransaction with single bill ----

    @Test
    void createTransaction_shouldCreateTransactionRecord() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);
        walletService.changeBalance(wallet.getId(), new BigDecimal("100.00"));

        Bill bill = createAndSaveBill(userId, new BigDecimal("10.00"));

        transactionService.createTransaction(List.of(bill.getId()));

        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        assertEquals(1, transactions.size());

        Transaction tx = transactions.get(0);
        assertEquals(userId, tx.getUserId());
        assertEquals(wallet.getId(), tx.getWalletId());
        assertEquals(0, new BigDecimal("10.00").compareTo(tx.getAmount()));
        assertEquals(TransactionType.CONSUMPTION, tx.getTransactionType());
        assertEquals(TransactionFlow.EXPENSE, tx.getTransactionFlow());
        assertEquals(TransactionStatus.PAID, tx.getTransactionStatus());
        assertEquals(bill.getId(), tx.getSourceId());
        assertNotNull(tx.getTransactionTime());
    }

    @Test
    void createTransaction_shouldDeductFromWallet() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);
        walletService.changeBalance(wallet.getId(), new BigDecimal("100.00"));

        Bill bill = createAndSaveBill(userId, new BigDecimal("25.00"));

        transactionService.createTransaction(List.of(bill.getId()));

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("75.00").compareTo(updated.getBalance()),
                "Wallet balance should be 100 - 25 = 75");
    }

    @Test
    void createTransaction_shouldRecordBalanceAfterDeduction() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);
        walletService.changeBalance(wallet.getId(), new BigDecimal("100.00"));

        Bill bill = createAndSaveBill(userId, new BigDecimal("40.00"));

        transactionService.createTransaction(List.of(bill.getId()));

        Transaction tx = mongoTemplate.findAll(Transaction.class).get(0);
        assertEquals(0, new BigDecimal("60.00").compareTo(tx.getBalance()),
                "Transaction balance should reflect wallet balance after deduction");
    }

    @Test
    void createTransaction_shouldUpdateBillStatusToCharged() {
        String userId = randomUserId();
        walletService.createWallet(userId);

        Bill bill = createAndSaveBill(userId, new BigDecimal("5.00"));
        assertEquals(BillStatus.CREATED, bill.getBillStatus());

        transactionService.createTransaction(List.of(bill.getId()));

        Bill updated = mongoTemplate.findById(bill.getId(), Bill.class);
        assertNotNull(updated);
        assertEquals(BillStatus.CHARGED, updated.getBillStatus());
    }

    @Test
    void createTransaction_shouldSetTransactionIdOnBill() {
        String userId = randomUserId();
        walletService.createWallet(userId);

        Bill bill = createAndSaveBill(userId, new BigDecimal("5.00"));

        transactionService.createTransaction(List.of(bill.getId()));

        Bill updated = mongoTemplate.findById(bill.getId(), Bill.class);
        assertNotNull(updated);
        assertNotNull(updated.getTransactionId());

        Transaction tx = mongoTemplate.findAll(Transaction.class).get(0);
        assertEquals(tx.getId(), updated.getTransactionId());
    }

    // ---- empty / nonexistent bills ----

    @Test
    void createTransaction_withEmptyList_shouldDoNothing() {
        transactionService.createTransaction(Collections.emptyList());

        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        assertTrue(transactions.isEmpty());
    }

    @Test
    void createTransaction_withNonExistentBillIds_shouldDoNothing() {
        transactionService.createTransaction(List.of(new ObjectId().toHexString()));

        List<Transaction> transactions = mongoTemplate.findAll(Transaction.class);
        assertTrue(transactions.isEmpty());
    }

    // ---- wallet auto-creation ----

    @Test
    void createTransaction_shouldAutoCreateWalletIfNotExists() {
        String userId = randomUserId();
        Bill bill = createAndSaveBill(userId, new BigDecimal("10.00"));

        transactionService.createTransaction(List.of(bill.getId()));

        Wallet wallet = walletService.getByUserId(userId);
        assertNotNull(wallet);
        assertEquals(0, new BigDecimal("-10.00").compareTo(wallet.getBalance()),
                "Wallet should have negative balance since it was auto-created with 0");
    }
}
