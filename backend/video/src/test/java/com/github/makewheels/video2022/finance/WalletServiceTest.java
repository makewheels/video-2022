package com.github.makewheels.video2022.finance;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.finance.wallet.Wallet;
import com.github.makewheels.video2022.finance.wallet.WalletService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WalletServiceTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    private String randomUserId() {
        return new ObjectId().toHexString();
    }

    // ---- createWallet ----

    @Test
    void createWallet_shouldCreateNewWalletWithZeroBalance() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        assertNotNull(wallet);
        assertNotNull(wallet.getId());
        assertEquals(userId, wallet.getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
        assertNotNull(wallet.getCreateTime());
        assertNotNull(wallet.getUpdateTime());
    }

    @Test
    void createWallet_shouldPersistToMongo() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        Wallet found = mongoTemplate.findById(wallet.getId(), Wallet.class);
        assertNotNull(found);
        assertEquals(wallet.getId(), found.getId());
        assertEquals(userId, found.getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(found.getBalance()));
    }

    @Test
    void createWallet_shouldBeIdempotent() {
        String userId = randomUserId();
        Wallet first = walletService.createWallet(userId);
        Wallet second = walletService.createWallet(userId);

        assertEquals(first.getId(), second.getId(),
                "Creating wallet twice for same user should return the same wallet");
    }

    // ---- getByUserId ----

    @Test
    void getByUserId_shouldReturnExistingWallet() {
        String userId = randomUserId();
        Wallet created = walletService.createWallet(userId);

        Wallet fetched = walletService.getByUserId(userId);

        assertEquals(created.getId(), fetched.getId());
        assertEquals(userId, fetched.getUserId());
    }

    @Test
    void getByUserId_shouldAutoCreateWalletIfNotExists() {
        String userId = randomUserId();
        Wallet wallet = walletService.getByUserId(userId);

        assertNotNull(wallet);
        assertNotNull(wallet.getId());
        assertEquals(userId, wallet.getUserId());
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
    }

    @Test
    void getByUserId_differentUsers_shouldGetDifferentWallets() {
        String userId1 = randomUserId();
        String userId2 = randomUserId();

        Wallet wallet1 = walletService.getByUserId(userId1);
        Wallet wallet2 = walletService.getByUserId(userId2);

        assertNotEquals(wallet1.getId(), wallet2.getId());
        assertEquals(userId1, wallet1.getUserId());
        assertEquals(userId2, wallet2.getUserId());
    }

    // ---- changeBalance ----

    @Test
    void changeBalance_shouldAddAmount() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        walletService.changeBalance(wallet.getId(), new BigDecimal("100.00"));

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("100.00").compareTo(updated.getBalance()));
    }

    @Test
    void changeBalance_shouldDeductAmount() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);
        walletService.changeBalance(wallet.getId(), new BigDecimal("100.00"));

        walletService.changeBalance(wallet.getId(), new BigDecimal("-30.00"));

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("70.00").compareTo(updated.getBalance()),
                "Balance should be 100 - 30 = 70");
    }

    @Test
    void changeBalance_multipleOperations_shouldAccumulate() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        walletService.changeBalance(wallet.getId(), new BigDecimal("200.00"));
        walletService.changeBalance(wallet.getId(), new BigDecimal("-50.00"));
        walletService.changeBalance(wallet.getId(), new BigDecimal("30.00"));

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("180.00").compareTo(updated.getBalance()),
                "200 - 50 + 30 = 180");
    }

    @Test
    void changeBalance_shouldAllowNegativeBalance() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        walletService.changeBalance(wallet.getId(), new BigDecimal("-10.00"));

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("-10.00").compareTo(updated.getBalance()));
    }

    @Test
    void changeBalance_withZeroAmount_shouldNotChangeBalance() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);
        walletService.changeBalance(wallet.getId(), new BigDecimal("50.00"));

        walletService.changeBalance(wallet.getId(), BigDecimal.ZERO);

        Wallet updated = walletService.getByUserId(userId);
        assertEquals(0, new BigDecimal("50.00").compareTo(updated.getBalance()));
    }

    @Test
    void changeBalance_shouldUpdateTimestamp() {
        String userId = randomUserId();
        Wallet wallet = walletService.createWallet(userId);

        walletService.changeBalance(wallet.getId(), new BigDecimal("10.00"));

        Wallet updated = walletService.getByUserId(userId);
        assertNotNull(updated.getUpdateTime(), "updateTime should be set after balance change");
    }
}
