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

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
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
     * 按前缀日期匹配，所以传入的时间是，北京时间的零点
     */
    private String getManifestKey(Date date) {
        log.info("获取manifest.json的key, 传入的时间 = " + DateUtil.formatDateTime(date));
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
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
     *     "creationTimestamp": "1694448644",
     *     "destinationBucket": "oss-data-bucket",
     *     "fileFormat": "CSV",
     *     "fileSchema": "Bucket, Key, Size, StorageClass, LastModifiedDate, ETag, IsMultipartUploaded, EncryptionStatus",
     *     "files": [{
     *             "MD5checksum": "5FC695B803A384A2FAA01D747C405FD1",
     *             "key": "video-2022-dev/inventory/video-2022-dev/inventory-rule/data/fc581f25-2ab5-4f11-a88a-5a74ec15241f.csv.gz",
     *             "size": 12586}],
     *     "sourceBucket": "video-2022-dev",
     *     "version": "2019-09-01"}
     */
    public List<String> getInventoryGzFileKeys(String manifestKey) {
        log.info("获取清单GZ压缩文件的key, manifestKey = " + manifestKey);
        String json = ossDataService.getObjectContent(manifestKey);
        JSONObject manifest = JSON.parseObject(json);
        List<String> gzFileKeys = manifest.getJSONArray("files").stream()
                .map(e -> ((JSONObject) e).getString("key"))
                .collect(Collectors.toList());
        log.info("获取到清单GZ压缩文件的key, gzFileKeys = " + JSON.toJSONString(gzFileKeys));
        return gzFileKeys;
    }

    /**
     * 获取csv文件
     */
    public List<File> getCsvFiles(List<String> gzKeys) {
        List<File> csvFiles = new ArrayList<>(gzKeys.size());
        for (String gzKey : gzKeys) {
            String csvFilename = FilenameUtils.getName(gzKey).replace(".gz", "");
            // 下载gz文件
            File gzFile = new File(FileUtils.getTempDirectory(), csvFilename + ".gz");
            ossDataService.downloadFile(gzKey, gzFile);
            log.info("下载gz文件到本地: " + gzFile.getAbsolutePath());
            // 解压gz文件
            CompressUtil.createExtractor(StandardCharsets.UTF_8, gzFile)
                    .extract(gzFile.getParentFile());
            File csvFile = new File(gzFile.getParentFile(), csvFilename);
            log.info("解压gz文件变成csv: " + csvFile.getAbsolutePath());
            gzFile.delete();
            log.info("删除gz文件: " + gzFile.getAbsolutePath());
            csvFiles.add(csvFile);
        }
        log.info("返回csv文件列表: " + JSON.toJSONString(csvFiles));
        return csvFiles;
    }

    /**
     * 把CSV清单文件解析成OssInventory清单对象
     * <a href="https://doc.hutool.cn/pages/CsvUtil">hutool读取CSV文档</a>
     */
    public List<OssInventory> parseFileToInventory(File file) {
        log.info("解析CSV " + file.getAbsolutePath());
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
        log.info("总共解析出inventoryList.size = " + inventoryList.size());
        return inventoryList;
    }

    /**
     * 获取清单
     */
    public List<OssInventory> getInventory(Date date) {
        String manifestKey = this.getManifestKey(date);
        List<String> inventoryGzFileKeys = this.getInventoryGzFileKeys(manifestKey);
        List<File> csvFiles = this.getCsvFiles(inventoryGzFileKeys);
        List<OssInventory> inventoryList = new ArrayList<>();
        for (File csvFile : csvFiles) {
            inventoryList.addAll(parseFileToInventory(csvFile));
            csvFile.delete();
        }
        return inventoryList;
    }
}