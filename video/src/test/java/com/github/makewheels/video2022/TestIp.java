package com.github.makewheels.video2022;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.utils.IpService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class TestIp {
    @Resource
    private IpService ipService;

    @Test
    public void test() {
        JSONObject ipWithRedis = ipService.getIpWithRedis("45.78.33.111");
        System.out.println(ipWithRedis);

        String province = ipWithRedis.getString("province");
        String city = ipWithRedis.getString("city");
        String district = ipWithRedis.getString("district");

        System.out.println("province = " + province);
        System.out.println("city = " + city);
        System.out.println("district = " + district);
    }
}
