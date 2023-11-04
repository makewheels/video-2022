package com.github.makewheels.video2022;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.oss.inventory.OssInventoryService;
import com.github.makewheels.video2022.oss.inventory.bean.GenerateInventoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * OSS快照
 */
@SpringBootTest(classes = VideoApplication.class)
public class OssInventoryTest {
    @Resource
    private OssInventoryService ossInventoryService;

    /**
     * 获取快照
     */
    @Test
    public void getInventory() {
        LocalDate date = LocalDate.of(2023, 10, 4);
        GenerateInventoryDTO generateInventoryDTO = ossInventoryService.generateInventory(date);
        System.out.println(JSON.toJSONString(generateInventoryDTO));
    }

    /**
     * 生成快照
     */
    @Test
    public void generateInventory() {
        LocalDate date = LocalDate.of(2023, 10, 4);
        ossInventoryService.generateAndSaveInventory(date);
    }

}
