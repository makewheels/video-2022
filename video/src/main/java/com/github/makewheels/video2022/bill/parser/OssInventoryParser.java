package com.github.makewheels.video2022.bill.parser;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.bill.bean.OssInventory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * oss清单解析
 */
@Service
public class OssInventoryParser {
    /**
     * <a href="https://doc.hutool.cn/pages/CsvUtil">hutool csv</a>
     */
    public List<OssInventory> parse(File file) {
        CsvData data = CsvUtil.getReader().read(file);
        List<OssInventory> inventoryList = new ArrayList<>(data.getRowCount());
        for (CsvRow row : data.getRows()) {
            OssInventory inventory = new OssInventory();
            inventory.setBucketName(row.get(0));
            inventory.setObjectName(URLUtil.decode(row.get(1)));
            inventory.setSize(Long.parseLong(row.get(2)));
            inventory.setStorageClass(row.get(3));
            inventory.setLastModifiedDate(DateUtil.parse(row.get(4), "yyyy-MM-dd'T'HH-mm-ss'Z'"));
            inventory.setETag(row.get(5));
            inventory.setIsMultipartUploaded(Boolean.parseBoolean(row.get(6)));
            inventory.setEncryptionStatus(Boolean.parseBoolean(row.get(7)));
            inventoryList.add(inventory);
        }
        return inventoryList;
    }

    public static void main(String[] args) {
        File file = new File("C:\\Users\\thedoflin\\Downloads\\" +
                "7b6f5016-7cd5-4d73-99d4-8257902cfc4a.csv");
        List<OssInventory> inventoryList = new OssInventoryParser().parse(file);
        for (OssInventory ossInventory : inventoryList) {
            System.out.println(JSON.toJSONString(ossInventory, true));
        }
    }
}
