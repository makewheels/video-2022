package com.github.makewheels.video2022.finance;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.access.FileAccessLog;
import com.github.makewheels.video2022.finance.bill.Bill;
import com.github.makewheels.video2022.finance.bill.BillRepository;
import com.github.makewheels.video2022.finance.bill.BillStatus;
import com.github.makewheels.video2022.finance.fee.base.FeeRepository;
import com.github.makewheels.video2022.finance.fee.base.FeeStatus;
import com.github.makewheels.video2022.finance.fee.base.FeeTypeEnum;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFee;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFeeService;
import com.github.makewheels.video2022.finance.fee.transcode.TranscodeFee;
import com.github.makewheels.video2022.finance.unitprice.UnitName;
import com.github.makewheels.video2022.finance.unitprice.UnitPriceService;
import com.github.makewheels.video2022.user.bean.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest extends BaseIntegrationTest {

    @Autowired
    private OssAccessFeeService ossAccessFeeService;

    @Autowired
    private UnitPriceService unitPriceService;

    @Autowired
    private FeeRepository feeRepository;

    @Autowired
    private BillRepository billRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    // ---- helpers ----

    private User createAndSaveUser() {
        User user = new User();
        user.setPhone("13800000099");
        return mongoTemplate.save(user);
    }

    private FileAccessLog buildAccessLog(String userId, long fileSize, Date accessTime) {
        FileAccessLog log = new FileAccessLog();
        log.setId(new ObjectId().toHexString());
        log.setUserId(userId);
        log.setKey("videos/test-video/raw/test.mp4");
        log.setStorageClass("Standard");
        log.setSize(fileSize);
        log.setCreateTime(accessTime);
        return log;
    }

    private OssAccessFee createAndSaveAccessFee(String userId, String videoId,
                                                 long fileSize, Date billTime) {
        FileAccessLog accessLog = buildAccessLog(userId, fileSize, billTime);
        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, null, videoId, "client-1", "session-1", "1080p",
                new ObjectId().toHexString());
        fee.setBillTime(billTime);
        return mongoTemplate.save(fee);
    }

    private TranscodeFee createAndSaveTranscodeFee(String userId, String videoId,
                                                    String resolution, long durationMs,
                                                    String provider, Date billTime) {
        TranscodeFee fee = new TranscodeFee();
        fee.setUserId(userId);
        fee.setVideoId(videoId);
        fee.setTranscodeId(new ObjectId().toHexString());
        fee.setResolution(resolution);
        fee.setDuration(durationMs);
        fee.setProvider(provider);
        fee.setBillTime(billTime);

        fee.setFeeType(FeeTypeEnum.TRANSCODE.getCode());
        fee.setFeeTypeName(FeeTypeEnum.TRANSCODE.getName());
        fee.setUnitName(UnitName.SECOND);

        BigDecimal unitPrice = new BigDecimal("0.0035");
        fee.setUnitPrice(unitPrice);
        BigDecimal amount = BigDecimal.valueOf(durationMs)
                .divide(BigDecimal.valueOf(1000), UnitPriceService.SCALE, RoundingMode.HALF_DOWN);
        fee.setAmount(amount);
        fee.setFeePrice(unitPrice.multiply(amount)
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN).abs());

        return mongoTemplate.save(fee);
    }

    // ---- OssAccessFee creation ----

    @Test
    void createOssAccessFee_shouldPopulateAllFields() {
        User user = createAndSaveUser();
        Date accessTime = new Date();
        long fileSize = 1024L * 1024 * 50; // 50 MB

        FileAccessLog accessLog = buildAccessLog(user.getId(), fileSize, accessTime);
        String videoId = new ObjectId().toHexString();
        String fileId = new ObjectId().toHexString();

        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, null, videoId, "client-1", "session-1", "720p", fileId);

        assertEquals(user.getId(), fee.getUserId());
        assertEquals(videoId, fee.getVideoId());
        assertEquals(fileId, fee.getFileId());
        assertEquals(accessLog.getId(), fee.getAccessId());
        assertEquals(accessLog.getKey(), fee.getKey());
        assertEquals("Standard", fee.getStorageClass());
        assertEquals(fileSize, fee.getFileSize());
        assertEquals(FeeTypeEnum.OSS_ACCESS.getCode(), fee.getFeeType());
        assertEquals(UnitName.GB, fee.getUnitName());
        assertEquals(FeeStatus.CREATED, fee.getFeeStatus());
        assertNotNull(fee.getUnitPrice());
        assertNotNull(fee.getAmount());
        assertNotNull(fee.getFeePrice());
    }

    @Test
    void createOssAccessFee_shouldPersistToMongo() {
        User user = createAndSaveUser();
        Date billTime = new Date();
        OssAccessFee saved = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 10, billTime);

        OssAccessFee found = mongoTemplate.findById(saved.getId(), OssAccessFee.class);
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals(saved.getUserId(), found.getUserId());
        assertEquals(0, saved.getFeePrice().compareTo(found.getFeePrice()));
    }

    // ---- TranscodeFee creation ----

    @Test
    void createTranscodeFee_shouldPopulateAllFields() {
        User user = createAndSaveUser();
        String videoId = new ObjectId().toHexString();
        Date billTime = new Date();

        TranscodeFee fee = createAndSaveTranscodeFee(
                user.getId(), videoId, "1080p", 120_000L, "ALIYUN", billTime);

        assertEquals(user.getId(), fee.getUserId());
        assertEquals(videoId, fee.getVideoId());
        assertEquals("1080p", fee.getResolution());
        assertEquals(120_000L, fee.getDuration());
        assertEquals("ALIYUN", fee.getProvider());
        assertEquals(FeeTypeEnum.TRANSCODE.getCode(), fee.getFeeType());
        assertEquals(UnitName.SECOND, fee.getUnitName());
        assertEquals(FeeStatus.CREATED, fee.getFeeStatus());
        assertNotNull(fee.getTranscodeId());
        assertNotNull(fee.getBillTime());
    }

    @Test
    void createTranscodeFee_shouldPersistToMongo() {
        User user = createAndSaveUser();
        TranscodeFee saved = createAndSaveTranscodeFee(
                user.getId(), new ObjectId().toHexString(),
                "720p", 60_000L, "CLOUD_FUNCTION", new Date());

        TranscodeFee found = mongoTemplate.findById(saved.getId(), TranscodeFee.class);
        assertNotNull(found);
        assertEquals(saved.getResolution(), found.getResolution());
        assertEquals(saved.getDuration(), found.getDuration());
        assertEquals(saved.getProvider(), found.getProvider());
    }

    // ---- fee price calculation ----

    @Test
    void feePriceCalculation_shouldEqualUnitPriceTimesAmount() {
        User user = createAndSaveUser();
        long fileSize = 1024L * 1024 * 100; // 100 MB
        Date accessTime = new Date();

        FileAccessLog accessLog = buildAccessLog(user.getId(), fileSize, accessTime);
        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, null, new ObjectId().toHexString(),
                "client-1", "session-1", "1080p", new ObjectId().toHexString());

        BigDecimal expected = fee.getUnitPrice()
                .multiply(fee.getAmount())
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN)
                .abs();

        assertEquals(0, expected.compareTo(fee.getFeePrice()),
                "feePrice must equal unitPrice × amount");
    }

    @Test
    void feePriceCalculation_transcodeFee_shouldEqualUnitPriceTimesAmount() {
        BigDecimal unitPrice = new BigDecimal("0.0035");
        long durationMs = 90_000L;
        BigDecimal amount = BigDecimal.valueOf(durationMs)
                .divide(BigDecimal.valueOf(1000), UnitPriceService.SCALE, RoundingMode.HALF_DOWN);
        BigDecimal expected = unitPrice.multiply(amount)
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN).abs();

        User user = createAndSaveUser();
        TranscodeFee fee = createAndSaveTranscodeFee(
                user.getId(), new ObjectId().toHexString(),
                "720p", durationMs, "ALIYUN", new Date());

        assertEquals(0, expected.compareTo(fee.getFeePrice()),
                "TranscodeFee feePrice must equal unitPrice × amount");
    }

    @Test
    void feePriceCalculation_shouldPreservePrecision() {
        User user = createAndSaveUser();
        long fileSize = 1L; // 1 byte — tiny amount to test precision
        Date accessTime = new Date();

        FileAccessLog accessLog = buildAccessLog(user.getId(), fileSize, accessTime);
        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, null, new ObjectId().toHexString(),
                "client-1", "session-1", "480p", new ObjectId().toHexString());

        assertTrue(fee.getFeePrice().scale() <= UnitPriceService.SCALE,
                "feePrice scale should not exceed configured SCALE");
        assertTrue(fee.getFeePrice().compareTo(BigDecimal.ZERO) > 0,
                "feePrice for 1 byte should still be positive");
    }

    // ---- bill creation from fees ----

    @Test
    void createBill_shouldGroupFeesByUser() {
        User user1 = createAndSaveUser();
        User user2 = new User();
        user2.setPhone("13800000088");
        mongoTemplate.save(user2);

        Date now = new Date();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        createAndSaveAccessFee(user1.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 20, yesterday);
        createAndSaveAccessFee(user1.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 30, yesterday);
        createAndSaveAccessFee(user2.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 40, yesterday);

        List<Bill> bills = ossAccessFeeService.createBill(billStart, billEnd);

        assertEquals(2, bills.size(), "Should create one bill per user");
        assertTrue(bills.stream().anyMatch(b -> b.getUserId().equals(user1.getId())));
        assertTrue(bills.stream().anyMatch(b -> b.getUserId().equals(user2.getId())));
    }

    @Test
    void createBill_totalShouldEqualSumOfFeePrices() {
        User user = createAndSaveUser();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        OssAccessFee fee1 = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 50, yesterday);
        OssAccessFee fee2 = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 80, yesterday);

        BigDecimal expectedOrigin = fee1.getFeePrice().add(fee2.getFeePrice())
                .setScale(UnitPriceService.SCALE, RoundingMode.HALF_DOWN).abs();

        List<Bill> bills = ossAccessFeeService.createBill(billStart, billEnd);

        assertEquals(1, bills.size());
        Bill bill = bills.get(0);
        assertEquals(0, expectedOrigin.compareTo(bill.getOriginChargePrice()),
                "originChargePrice must equal sum of fee prices");
        assertEquals(2, bill.getFeeCount());

        // realChargePrice should be originChargePrice rounded to 2 decimal places
        BigDecimal expectedReal = expectedOrigin
                .setScale(2, RoundingMode.HALF_DOWN);
        assertEquals(0, expectedReal.compareTo(bill.getRealChargePrice()),
                "realChargePrice must be origin rounded to 2 decimals");
    }

    @Test
    void createBill_shouldSetFeeStatusToCharged() {
        User user = createAndSaveUser();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        OssAccessFee fee = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 25, yesterday);

        assertEquals(FeeStatus.CREATED, fee.getFeeStatus(),
                "Fee status should be CREATED before billing");

        ossAccessFeeService.createBill(billStart, billEnd);

        OssAccessFee updated = mongoTemplate.findById(fee.getId(), OssAccessFee.class);
        assertNotNull(updated);
        assertEquals(FeeStatus.CHARGED, updated.getFeeStatus(),
                "Fee status should change to CHARGED after billing");
    }

    @Test
    void createBill_shouldSetBillIdOnEachFee() {
        User user = createAndSaveUser();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        OssAccessFee fee1 = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 10, yesterday);
        OssAccessFee fee2 = createAndSaveAccessFee(user.getId(),
                new ObjectId().toHexString(), 1024L * 1024 * 15, yesterday);

        assertNull(fee1.getBillId(), "billId should be null before billing");
        assertNull(fee2.getBillId(), "billId should be null before billing");

        List<Bill> bills = ossAccessFeeService.createBill(billStart, billEnd);
        assertEquals(1, bills.size());
        String billId = bills.get(0).getId();
        assertNotNull(billId);

        OssAccessFee updatedFee1 = mongoTemplate.findById(fee1.getId(), OssAccessFee.class);
        OssAccessFee updatedFee2 = mongoTemplate.findById(fee2.getId(), OssAccessFee.class);

        assertNotNull(updatedFee1);
        assertNotNull(updatedFee2);
        assertEquals(billId, updatedFee1.getBillId(),
                "billId should be set on fee after billing");
        assertEquals(billId, updatedFee2.getBillId(),
                "billId should be set on fee after billing");
    }

    @Test
    void createBill_withNoFees_shouldReturnEmptyList() {
        createAndSaveUser();
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        List<Bill> bills = ossAccessFeeService.createBill(billStart, billEnd);

        assertTrue(bills.isEmpty(), "No bills should be created when there are no fees");
    }

    @Test
    void createBill_billStatusShouldBeCreated() {
        User user = createAndSaveUser();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        createAndSaveAccessFee(user.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 30, yesterday);

        List<Bill> bills = ossAccessFeeService.createBill(billStart, billEnd);

        assertEquals(1, bills.size());
        Bill bill = bills.get(0);
        assertEquals(BillStatus.CREATED, bill.getBillStatus(),
                "New bill status should be CREATED");
        assertEquals(FeeTypeEnum.OSS_ACCESS.getCode(), bill.getFeeType());
        assertEquals(FeeTypeEnum.OSS_ACCESS.getName(), bill.getFeeTypeName());
        assertNotNull(bill.getChargeTime());
    }

    @Test
    void createBill_shouldNotIncludeAlreadyChargedFees() {
        User user = createAndSaveUser();
        Date yesterday = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date billStart = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date billEnd = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        // First fee — will be billed
        createAndSaveAccessFee(user.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 20, yesterday);

        // Bill once
        List<Bill> firstBills = ossAccessFeeService.createBill(billStart, billEnd);
        assertEquals(1, firstBills.size());

        // Add a second fee
        createAndSaveAccessFee(user.getId(), new ObjectId().toHexString(),
                1024L * 1024 * 30, yesterday);

        // Bill again — should only pick up the new fee
        List<Bill> secondBills = ossAccessFeeService.createBill(billStart, billEnd);
        assertEquals(1, secondBills.size());
        assertEquals(1, secondBills.get(0).getFeeCount(),
                "Second billing should only include the new uncharged fee");
    }
}
