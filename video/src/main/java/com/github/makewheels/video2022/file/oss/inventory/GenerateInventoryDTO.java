package com.github.makewheels.video2022.file.oss.inventory;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class GenerateInventoryDTO {
    private List<OssInventory> ossInventoryList;
    private JSONObject manifestJson;
}
