package com.github.makewheels.video2022;

import com.github.makewheels.video2022.archive.SignUtil;
import org.junit.jupiter.api.Test;

/**
 * 测试加签
 */
public class TestSign {
    @Test
    public void hmac() {
        for (int i = 0; i < 5; i++) {
            String sign = SignUtil.hmac("123", "abc");
            System.out.println(sign);
        }
    }
}
