package com.github.makewheels.video2022.finance;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.finance.unitprice.UnitPrice;
import com.github.makewheels.video2022.finance.unitprice.UnitPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class UnitPriceServiceTest extends BaseIntegrationTest {

    @Autowired
    private UnitPriceService unitPriceService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    private Date dateAtHour(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private BigDecimal expectedPerBytePrice(String pricePerGb) {
        return new BigDecimal(pricePerGb)
                .divide(new BigDecimal("1024").pow(3), UnitPriceService.SCALE, RoundingMode.HALF_DOWN);
    }

    // ---- low traffic period (00:00-07:59) → ¥0.25/GB ----

    @Test
    void getOssAccessUnitPrice_atMidnight_shouldReturnLowPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(0));
        assertEquals(0, expectedPerBytePrice("0.25").compareTo(result.getUnitPrice()));
    }

    @Test
    void getOssAccessUnitPrice_atHour3_shouldReturnLowPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(3));
        assertEquals(0, expectedPerBytePrice("0.25").compareTo(result.getUnitPrice()));
    }

    @Test
    void getOssAccessUnitPrice_atHour7_boundary_shouldReturnLowPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(7));
        assertEquals(0, expectedPerBytePrice("0.25").compareTo(result.getUnitPrice()),
                "Hour 7 (07:xx) is the last low-traffic hour");
    }

    // ---- high traffic period (08:00-23:59) → ¥0.50/GB ----

    @Test
    void getOssAccessUnitPrice_atHour8_boundary_shouldReturnHighPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(8));
        assertEquals(0, expectedPerBytePrice("0.50").compareTo(result.getUnitPrice()),
                "Hour 8 (08:xx) is the first high-traffic hour");
    }

    @Test
    void getOssAccessUnitPrice_atNoon_shouldReturnHighPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(12));
        assertEquals(0, expectedPerBytePrice("0.50").compareTo(result.getUnitPrice()));
    }

    @Test
    void getOssAccessUnitPrice_atHour23_shouldReturnHighPrice() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(23));
        assertEquals(0, expectedPerBytePrice("0.50").compareTo(result.getUnitPrice()));
    }

    // ---- price relationship ----

    @Test
    void getOssAccessUnitPrice_highTrafficShouldBeDoubleLowTraffic() {
        UnitPrice low = unitPriceService.getOssAccessUnitPrice(dateAtHour(3));
        UnitPrice high = unitPriceService.getOssAccessUnitPrice(dateAtHour(12));
        BigDecimal ratio = high.getUnitPrice()
                .divide(low.getUnitPrice(), 10, RoundingMode.HALF_DOWN);
        assertEquals(0, new BigDecimal("2").compareTo(ratio),
                "High traffic price should be exactly 2× low traffic price");
    }

    // ---- general properties ----

    @Test
    void getOssAccessUnitPrice_shouldAlwaysReturnPositivePrice() {
        for (int hour = 0; hour < 24; hour++) {
            UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(hour));
            assertNotNull(result, "Result should not be null for hour " + hour);
            assertNotNull(result.getUnitPrice(), "UnitPrice should not be null for hour " + hour);
            assertTrue(result.getUnitPrice().compareTo(BigDecimal.ZERO) > 0,
                    "Unit price should be positive for hour " + hour);
        }
    }

    @Test
    void getOssAccessUnitPrice_shouldPreservePrecision() {
        UnitPrice result = unitPriceService.getOssAccessUnitPrice(dateAtHour(0));
        assertTrue(result.getUnitPrice().scale() <= UnitPriceService.SCALE,
                "Scale should not exceed configured SCALE of " + UnitPriceService.SCALE);
    }
}
