package com.github.makewheels.video2022;

import com.github.makewheels.video2022.finance.bill.CreateBillTask;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class CreateBillTaskTests {
    @Resource
    private CreateBillTask createBillTask;

    @Test
    public void createAccessFeeBill() {
        createBillTask.createAccessFeeBill();
    }
}
