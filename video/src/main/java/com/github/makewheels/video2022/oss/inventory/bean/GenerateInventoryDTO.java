package com.github.makewheels.video2022.oss.inventory.bean;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GenerateInventoryDTO {
    private LocalDate date;
    private String programBatchId;
    private String manifestKey;
    private JSONObject manifest;
    private List<String> gzFileKeys;
}
