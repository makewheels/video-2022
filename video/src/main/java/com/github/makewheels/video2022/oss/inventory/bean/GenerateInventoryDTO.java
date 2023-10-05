package com.github.makewheels.video2022.oss.inventory.bean;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class GenerateInventoryDTO {
    private OssInventory ossInventory;
    private List<OssInventoryItem> ossInventoryItemList;
}
