package com.github.makewheels.video2022;

import com.github.makewheels.video2022.id.IdService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class TestId {
    @Resource
    private IdService idService;

    @Test
    public void test() {
        for (int i = 0; i < 3; i++) {
            System.out.println(idService.nextId());
        }
    }
}