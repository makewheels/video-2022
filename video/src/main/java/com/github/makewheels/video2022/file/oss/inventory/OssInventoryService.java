package com.github.makewheels.video2022.file.oss.inventory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.compress.CompressUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.etc.springboot.exception.VideoException;
import com.github.makewheels.video2022.file.oss.OssDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sun.util.calendar.ZoneInfo;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * oss清单解析
 */
@Service
@Slf4j
public class OssInventoryService {

    @Value("${aliyun.oss.data.inventory-prefix}")
    private String inventoryPrefix;

    @Resource
    private OssDataService ossDataService;

    /**
     * 获取manifest.json的key
     */
    private String getManifestKey(Date date) {
        log.info("获取manifest.json的key, 传入的时间 = " + DateUtil.formatDateTime(date));
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(ZoneInfo.getTimeZone("UTC"));
        String utcDate = simpleDateFormat.format(date);
        List<OSSObjectSummary> ossObjectList
                = ossDataService.listAllObjects(inventoryPrefix + "/" + utcDate);
        OSSObjectSummary ossObjectSummary = ossObjectList.stream()
                .filter(e -> FilenameUtils.getName(e.getKey()).equals("manifest.json"))
                .findFirst().orElseThrow(() -> new VideoException("找不到manifest.json"));
        log.info("找到manifest.json, ossObject = " + JSON.toJSONString(ossObjectSummary));
        return ossObjectSummary.getKey();
    }

    /**
     * 获取清单文件的key
     * {
     * "creationTimestamp": "1694305241",
     * "destinationBucket": "oss-data-bucket",
     * "fileFormat": "CSV",
     * "fileSchema": "Bucket, Key, Size, StorageClass, LastModifiedDate, ETag,
     * IsMultipartUploaded, EncryptionStatus",
     * "files": [{
     * "MD5checksum": "5FC695B803A384A2FAA01D747C405FD1",
     * "key": "video-2022-dev/inventory/video-2022-dev/inventory-rule
     * /data/2a755fbb-b920-4347-b99b-add10258972e.csv.gz",
     * "size": 12586
     * }],
     * "sourceBucket": "video-2022-dev",
     * "version": "2019-09-01"
     * }
     */
    public List<String> getInventoryKeys(String manifestKey) {
        String json = ossDataService.getObjectContent(manifestKey);
        JSONObject manifest = JSON.parseObject(json);
        return manifest.getJSONArray("files").stream()
                .map(e -> ((JSONObject) e).getString("key"))
                .collect(Collectors.toList());
    }

    /**
     * 获取csv文件
     */
    public List<File> getCsvFiles(List<String> gzKeys) {
        List<File> csvFiles = new ArrayList<>(gzKeys.size());
        for (String gzKey : gzKeys) {
            // 下载gz文件
            File gzFile = new File(FileUtils.getTempDirectory(), FilenameUtils.getName(gzKey));
            // 解压gz文件
            CompressUtil.createExtractor(StandardCharsets.UTF_8, gzFile)
                    .extract(gzFile.getParentFile());
            // TODO 怎么找到csv文件
            File csvFile = new File(gzFile.getParentFile(), "data.csv");
            gzFile.delete();
            csvFiles.add(csvFile);
        }

        return csvFiles;
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

}