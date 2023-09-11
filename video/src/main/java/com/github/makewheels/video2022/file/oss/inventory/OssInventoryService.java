package com.github.makewheels.video2022.file.oss.inventory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.file.oss.OssDataService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * oss清单解析
 * {
 *     "creationTimestamp": "1694305241",
 *     "destinationBucket": "oss-data-bucket",
 *     "fileFormat": "CSV",
 *     "fileSchema": "Bucket, Key, Size, StorageClass, LastModifiedDate, ETag,
 *                    IsMultipartUploaded, EncryptionStatus",
 *     "files": [{
 *             "MD5checksum": "5FC695B803A384A2FAA01D747C405FD1",
 *             "key": "video-2022-dev/inventory/video-2022-dev/inventory-rule
 *                     /data/2a755fbb-b920-4347-b99b-add10258972e.csv.gz",
 *             "size": 12586
 *      }],
 *     "sourceBucket": "video-2022-dev",
 *     "version": "2019-09-01"
 * }
 */
@Service
public class OssInventoryService {

    @Value("${aliyun.oss.data.inventory-prefix}")
    private String inventoryPrefix;

    @Resource
    private OssDataService ossDataService;

    /**
     * 获取manifest.json的key
     */
    private String getInventoryPrefix(Date date) {
        // 把传入时间转为UTC日期
        String utcDate = DateUtil.format(date, "yyyy-MM-dd'T'HH-mm'Z'");
        // 获取UTC日期
        String utcDatePrefix = utcDate.substring(0, 10);
        List<OSSObjectSummary> ossObjectSummaries = ossDataService.listAllObjects(
                inventoryPrefix + "/" + utcDatePrefix);
        if (CollectionUtils.isEmpty(ossObjectSummaries)) {
            return null;
        }
        OSSObjectSummary ossObjectSummary = ossObjectSummaries.stream()
                .filter(e -> FilenameUtils.getName(e.getKey()).equals("manifest.json"))
                .findFirst().orElse(null);
        Assert.notNull(ossObjectSummary, "找不到manifest.json");
        return ossObjectSummary.getKey();
    }

    /**
     * 把CSV清单文件解析成OssInventory清单对象
     * <a href="https://doc.hutool.cn/pages/CsvUtil">hutool读取CSV文档</a>
     */
    public List<OssInventory> parseFileToInventory(File file) {
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
        List<OssInventory> inventoryList = new OssInventoryService().parseFileToInventory(file);
        for (OssInventory ossInventory : inventoryList) {
            System.out.println(JSON.toJSONString(ossInventory, true));
        }
    }
}
