package com.github.makewheels.video2022.finance;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.file.access.FileAccessLog;
import com.github.makewheels.video2022.finance.bill.Bill;
import com.github.makewheels.video2022.finance.bill.BillStatus;
import com.github.makewheels.video2022.finance.fee.base.FeeStatus;
import com.github.makewheels.video2022.finance.fee.base.FeeTypeEnum;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFee;
import com.github.makewheels.video2022.finance.fee.ossaccess.OssAccessFeeService;
import com.github.makewheels.video2022.finance.unitprice.UnitName;
import com.github.makewheels.video2022.user.bean.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OssAccessFeeServiceTest extends BaseIntegrationTest {

    @Autowired
    private OssAccessFeeService ossAccessFeeService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000003");
        testUser.setRegisterChannel("TEST");
        mongoTemplate.save(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private FileAccessLog createTestAccessLog(Date accessTime, long fileSize) {
        FileAccessLog accessLog = new FileAccessLog();
        accessLog.setUserId(testUser.getId());
        accessLog.setKey("videos/abc/transcode/720p/seg-0.ts");
        accessLog.setSize(fileSize);
        accessLog.setStorageClass("Standard");
        accessLog.setCreateTime(accessTime);
        mongoTemplate.save(accessLog);
        return accessLog;
    }

    private Date createDateAtHour(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    // ──────────────────── create ────────────────────

    @Test
    void create_setsCorrectFields() {
        Date accessTime = createDateAtHour(10);
        FileAccessLog accessLog = createTestAccessLog(accessTime, 102400L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, request, "v_test_001", "client_001", "session_001",
                "720p", "f_test_001");

        assertEquals(testUser.getId(), fee.getUserId());
        assertEquals("v_test_001", fee.getVideoId());
        assertEquals("f_test_001", fee.getFileId());
        assertEquals(accessLog.getId(), fee.getAccessId());
        assertEquals(accessLog.getKey(), fee.getKey());
        assertEquals("Standard", fee.getStorageClass());
        assertEquals(102400L, fee.getFileSize());
        assertEquals(accessTime, fee.getBillTime());
        assertEquals(FeeTypeEnum.OSS_ACCESS.getCode(), fee.getFeeType());
        assertEquals(FeeTypeEnum.OSS_ACCESS.getName(), fee.getFeeTypeName());
        assertEquals(UnitName.GB, fee.getUnitName());
        assertNotNull(fee.getUnitPrice());
        assertNotNull(fee.getAmount());
        assertEquals(BigDecimal.valueOf(102400L), fee.getAmount());
        assertNotNull(fee.getFeePrice());
        assertTrue(fee.getFeePrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void create_busyHourPrice_isHigherThanIdleHour() {
        long fileSize = 1024 * 1024L; // 1 MB
        FileAccessLog busyLog = createTestAccessLog(createDateAtHour(10), fileSize);
        FileAccessLog idleLog = createTestAccessLog(createDateAtHour(3), fileSize);

        MockHttpServletRequest request = new MockHttpServletRequest();

        OssAccessFee busyFee = ossAccessFeeService.create(
                busyLog, request, "v1", "c1", "s1", "720p", "f1");
        OssAccessFee idleFee = ossAccessFeeService.create(
                idleLog, request, "v2", "c2", "s2", "720p", "f2");

        // Busy hour (10:00) = 0.50/GB, idle hour (03:00) = 0.25/GB
        assertTrue(busyFee.getFeePrice().compareTo(idleFee.getFeePrice()) > 0,
                "Busy hour fee should be higher than idle hour fee");
        // Busy unit price should be double idle unit price
        assertTrue(busyFee.getUnitPrice().compareTo(idleFee.getUnitPrice()) > 0,
                "Busy hour unit price should be higher");
    }

    @Test
    void create_feePriceIsNonNegative() {
        FileAccessLog accessLog = createTestAccessLog(createDateAtHour(12), 512L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, request, "v1", "c1", "s1", "720p", "f1");

        assertTrue(fee.getFeePrice().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void create_defaultFeeStatus_isCreated() {
        FileAccessLog accessLog = createTestAccessLog(createDateAtHour(14), 1024L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        OssAccessFee fee = ossAccessFeeService.create(
                accessLog, request, "v1", "c1", "s1", "720p", "f1");

        assertEquals(FeeStatus.CREATED, fee.getFeeStatus());
    }

    // ──────────────────── createBill ────────────────────

    @Test
    void createBill_createsOneBillPerUserWithFees() {
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.HOUR, -1);
        Date oneHourAgo = cal.getTime();
        cal.add(Calendar.HOUR, 2);
        Date oneHourLater = cal.getTime();

        // Create and save two OssAccessFee records for testUser
        OssAccessFee fee1 = new OssAccessFee();
        fee1.setUserId(testUser.getId());
        fee1.setVideoId("v_bill_001");
        fee1.setFileId("f_bill_001");
        fee1.setBillTime(now);
        fee1.setFeeStatus(FeeStatus.CREATED);
        fee1.setFeeType(FeeTypeEnum.OSS_ACCESS.getCode());
        fee1.setFeeTypeName(FeeTypeEnum.OSS_ACCESS.getName());
        fee1.setUnitPrice(new BigDecimal("0.00000000046566128730"));
        fee1.setAmount(BigDecimal.valueOf(102400L));
        fee1.setFeePrice(new BigDecimal("0.00004768371582031250"));
        mongoTemplate.save(fee1);

        OssAccessFee fee2 = new OssAccessFee();
        fee2.setUserId(testUser.getId());
        fee2.setVideoId("v_bill_002");
        fee2.setFileId("f_bill_002");
        fee2.setBillTime(now);
        fee2.setFeeStatus(FeeStatus.CREATED);
        fee2.setFeeType(FeeTypeEnum.OSS_ACCESS.getCode());
        fee2.setFeeTypeName(FeeTypeEnum.OSS_ACCESS.getName());
        fee2.setUnitPrice(new BigDecimal("0.00000000046566128730"));
        fee2.setAmount(BigDecimal.valueOf(204800L));
        fee2.setFeePrice(new BigDecimal("0.00009536743164062500"));
        mongoTemplate.save(fee2);

        List<Bill> bills = ossAccessFeeService.createBill(oneHourAgo, oneHourLater);

        assertEquals(1, bills.size());
        Bill bill = bills.get(0);
        assertEquals(testUser.getId(), bill.getUserId());
        assertEquals(FeeTypeEnum.OSS_ACCESS.getCode(), bill.getFeeType());
        assertEquals(2, bill.getFeeCount());
        assertNotNull(bill.getOriginChargePrice());
        assertNotNull(bill.getRoundDownPrice());
        assertNotNull(bill.getRealChargePrice());
        assertEquals(BillStatus.CREATED, bill.getBillStatus());

        // Verify bill was persisted
        Bill fromDb = mongoTemplate.findById(bill.getId(), Bill.class);
        assertNotNull(fromDb);
        assertEquals(bill.getFeeCount(), fromDb.getFeeCount());
    }

    @Test
    void createBill_noFeesForUser_returnsEmptyList() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -1);
        Date start = cal.getTime();
        cal.add(Calendar.HOUR, 2);
        Date end = cal.getTime();

        List<Bill> bills = ossAccessFeeService.createBill(start, end);
        assertTrue(bills.isEmpty());
    }

    @Test
    void createBill_updatesFeeStatusToCharged() {
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.HOUR, -1);
        Date start = cal.getTime();
        cal.setTime(now);
        cal.add(Calendar.HOUR, 1);
        Date end = cal.getTime();

        OssAccessFee fee = new OssAccessFee();
        fee.setUserId(testUser.getId());
        fee.setVideoId("v_status_001");
        fee.setFileId("f_status_001");
        fee.setBillTime(now);
        fee.setFeeStatus(FeeStatus.CREATED);
        fee.setFeeType(FeeTypeEnum.OSS_ACCESS.getCode());
        fee.setFeeTypeName(FeeTypeEnum.OSS_ACCESS.getName());
        fee.setUnitPrice(new BigDecimal("0.00000000046566128730"));
        fee.setAmount(BigDecimal.valueOf(1024L));
        fee.setFeePrice(new BigDecimal("0.00000047683715820312"));
        mongoTemplate.save(fee);

        List<Bill> bills = ossAccessFeeService.createBill(start, end);
        assertFalse(bills.isEmpty());

        // Fee status should be updated to CHARGED
        OssAccessFee updatedFee = mongoTemplate.findById(fee.getId(), OssAccessFee.class);
        assertNotNull(updatedFee);
        assertEquals(FeeStatus.CHARGED, updatedFee.getFeeStatus());
        assertEquals(bills.get(0).getId(), updatedFee.getBillId());
    }

    @Test
    void createBill_realChargePriceRoundsToTwoDecimals() {
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.HOUR, -1);
        Date start = cal.getTime();
        cal.setTime(now);
        cal.add(Calendar.HOUR, 1);
        Date end = cal.getTime();

        OssAccessFee fee = new OssAccessFee();
        fee.setUserId(testUser.getId());
        fee.setVideoId("v_round_001");
        fee.setFileId("f_round_001");
        fee.setBillTime(now);
        fee.setFeeStatus(FeeStatus.CREATED);
        fee.setFeeType(FeeTypeEnum.OSS_ACCESS.getCode());
        fee.setFeeTypeName(FeeTypeEnum.OSS_ACCESS.getName());
        fee.setUnitPrice(new BigDecimal("0.00000000046566128730"));
        fee.setAmount(BigDecimal.valueOf(102400L));
        fee.setFeePrice(new BigDecimal("0.00004768371582031250"));
        mongoTemplate.save(fee);

        List<Bill> bills = ossAccessFeeService.createBill(start, end);
        Bill bill = bills.get(0);

        // realChargePrice should have scale of 2
        assertTrue(bill.getRealChargePrice().scale() <= 2,
                "realChargePrice should have at most 2 decimal places");
    }
}
