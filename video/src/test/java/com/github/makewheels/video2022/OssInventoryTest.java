package com.github.makewheels.video2022;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.oss.inventory.OssInventoryService;
import com.github.makewheels.video2022.oss.inventory.bean.GenerateInventoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import javax.annotation.Resource;

/**
 * OSS快照
 */
@SpringBootTest
public class OssInventoryTest {
    @Resource
    private OssInventoryService ossInventoryService;

    /**
     * 获取快照
     */
    @Test
    public void getInventory() {
        Date date = DateUtil.parse("2023-10-04 15:30:00");
        GenerateInventoryDTO generateInventoryDTO = ossInventoryService.getGenerateInventoryDTO(date);
        System.out.println(JSON.toJSONString(generateInventoryDTO));
    }

}
