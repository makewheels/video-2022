package com.github.makewheels.video2022.oss.inventory;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.etc.springboot.exception.VideoException;
import com.github.makewheels.video2022.oss.OssDataService;
import com.github.makewheels.video2022.oss.inventory.bean.GenerateInventoryDTO;
import com.github.makewheels.video2022.oss.inventory.bean.OssInventory;
import com.github.makewheels.video2022.oss.inventory.bean.OssInventoryItem;
import com.github.makewheels.video2022.utils.CompressUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
     * 按前缀日期在OSS搜索
     * 传入时间北京时间的零点，会转为UTC时间
     */
    private String getManifestKey(LocalDate date) {
        log.info("获取manifest.json的key, 传入的时间 = " + date);
        ZonedDateTime utcDateTime = date.atStartOfDay(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN);
        String utcDate = formatter.format(utcDateTime);

        String manifestPrefix = inventoryPrefix + "/" + utcDate;
        List<OSSObjectSummary> ossObjectList = ossDataService.listAllObjects(manifestPrefix);
        Assert.notEmpty(ossObjectList, "找不到清单文件, manifestPrefix = " + manifestPrefix);

        OSSObjectSummary ossObjectSummary = ossObjectList.stream()
                .filter(e -> FilenameUtils.getName(e.getKey()).equals("manifest.json"))
                .findFirst().orElseThrow(() -> new VideoException("找不到manifest.json"));
        log.info("找到manifest.json, ossObject = " + JSON.toJSONString(ossObjectSummary));
        String manifestKey = ossObjectSummary.getKey();
        log.info("获取清单GZ压缩文件的key, manifestKey = " + manifestKey);
        return manifestKey;
    }

    /**
     * 解析清单文件，获取清单GZ压缩文件的key
     * {
     * "creationTimestamp":"1694448644",
     * "destinationBucket":"oss-data-bucket",
     * "fileFormat":"CSV",
     * "fileSchema":"Bucket, Key, Size, StorageClass, LastModifiedDate, ETag,
     * IsMultipartUploaded, EncryptionStatus",
     * "files":[
     * {
     * "MD5checksum":"5FC695B803A384A2FAA01D747C405FD1",
     * "key":"video-2022-dev/inventory/video-2022-dev/inventory-rule/data
     * /fc581f25-2ab5-4f11-a88a-5a74ec15241f.csv.gz",
     * "size":12586
     * }
     * ],
     * "sourceBucket":"video-2022-dev",
     * "version":"2019-09-01"
     * }
     */
    public List<String> getInventoryGzFileKeys(JSONObject manifest) {
        List<String> gzFileKeys = manifest.getJSONArray("files").stream()
                .map(e -> ((JSONObject) e).getString("key"))
                .collect(Collectors.toList());
        log.info("获取到清单GZ压缩文件的key, gzFileKeys = " + JSON.toJSONString(gzFileKeys));
        return gzFileKeys;
    }

    /**
     * 获取csv文件
     * <a href="https://doc.hutool.cn/pages/ZipUtil/">压缩工具-ZipUtil</a>
     */
    public List<File> getCsvFiles(List<String> gzKeys) {
        List<File> csvFiles = new ArrayList<>(gzKeys.size());
        for (String gzKey : gzKeys) {
            String csvFilename = FilenameUtils.getName(gzKey).replace(".gz", "");
            // 下载gz文件
            File gzFile = new File(FileUtils.getTempDirectory(), csvFilename + ".gz");
            ossDataService.downloadFile(gzKey, gzFile);
            log.info("下载gz文件到本地，文件大小: " + FileUtil.readableFileSize(gzFile)
                    + "，文件路径: " + gzFile.getAbsolutePath());
            // 解压gz文件
            File targetFolder = new File(gzFile.getParentFile(), IdUtil.fastSimpleUUID());
            CompressUtil.unCompressGz(gzFile, targetFolder);

            Assert.isTrue(FileUtil.loopFiles(targetFolder).size() == 1,
                    "解压出来的文件数量不是1");
            File csvFile = FileUtil.loopFiles(targetFolder).get(0);
            log.info("解压gz文件变成csv文件: " + csvFile.getAbsolutePath());
            FileUtil.del(gzFile);
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
    public List<OssInventoryItem> parseCsvFileToInventory(OssInventory inventory, List<File> csvFiles) {
        List<OssInventoryItem> inventoryItemList = new ArrayList<>();
        for (File csvFile : csvFiles) {
            log.info("解析CSV文件: " + csvFile.getAbsolutePath());
            CsvData data = CsvUtil.getReader().read(csvFile);
            for (CsvRow row : data.getRows()) {
                OssInventoryItem inventoryItem = new OssInventoryItem();
                // 解析CSV文件
                inventoryItem.setBucketName(row.get(0));
                inventoryItem.setObjectName(URLUtil.decode(row.get(1)));
                inventoryItem.setSize(Long.parseLong(row.get(2)));
                inventoryItem.setStorageClass(row.get(3));
                DateTime lastModifiedDate = DateUtil.parse(row.get(4), "yyyy-MM-dd'T'HH-mm-ss'Z'");
                inventoryItem.setLastModifiedDate(lastModifiedDate);
                inventoryItem.setETag(row.get(5));
                inventoryItem.setIsMultipartUploaded(Boolean.parseBoolean(row.get(6)));
                inventoryItem.setEncryptionStatus(Boolean.parseBoolean(row.get(7)));

                // 设置inventory父级字段
                inventoryItem.setAliyunGenerationTime(inventory.getAliyunGenerationTime());
                inventoryItem.setInventoryGenerationDate(inventory.getInventoryGenerationDate());
                inventoryItemList.add(inventoryItem);
            }
            log.info("解析出inventoryItemList.size = " + inventoryItemList.size());
        }
        return inventoryItemList;
    }

    /**
     * 初始化 GenerateInventoryDTO, 获取manifest和CSV文件
     */
    private GenerateInventoryDTO initGenerateInventoryDTO(LocalDate date) {
        // 获取快照，解析出inventoryItemList
        String manifestKey = this.getManifestKey(date);
        JSONObject manifest = JSON.parseObject(ossDataService.getObjectContent(manifestKey));
        log.info("获取到清单文件的内容, manifest = " + JSON.toJSONString(manifest));
        List<String> gzFileKeys = this.getInventoryGzFileKeys(manifest);

        GenerateInventoryDTO generateInventoryDTO = new GenerateInventoryDTO();
        generateInventoryDTO.setDate(date);
        generateInventoryDTO.setManifestKey(manifestKey);
        generateInventoryDTO.setManifest(manifest);
        generateInventoryDTO.setGzFileKeys(gzFileKeys);
        return generateInventoryDTO;
    }

    /**
     * 创建 OssInventory
     */
    private OssInventory createOssInvetory(GenerateInventoryDTO generateInventoryDTO) {
        JSONObject manifest = generateInventoryDTO.getManifest();
        OssInventory inventory = new OssInventory();
        inventory.setSnapshotSourceBucket(manifest.getString("sourceBucket"));
        inventory.setInventoryStorageBucket(ossDataService.getBucket());
        inventory.setGzOssKeys(generateInventoryDTO.getGzFileKeys());
        inventory.setManifestKey(generateInventoryDTO.getManifestKey());
        inventory.setManifest(manifest);

        long creationTimestampInMillis = Long.parseLong(
                manifest.getString("creationTimestamp")) * 1000;
        inventory.setAliyunGenerationTime(new Date(creationTimestampInMillis));

        inventory.setInventoryGenerationDate(Integer.valueOf(DateUtil.format(
                inventory.getAliyunGenerationTime(), DatePattern.PURE_DATE_PATTERN)));

        return inventory;
    }

    /**
     * 解析 OssInventoryItem
     */
    private List<OssInventoryItem> getOssInventoryItems(GenerateInventoryDTO generateInventoryDTO) {
        List<File> csvFiles = this.getCsvFiles(generateInventoryDTO.getGzFileKeys());
        List<OssInventoryItem> inventoryItemList = parseCsvFileToInventory(
                generateInventoryDTO.getOssInventory(), csvFiles);
        for (File csvFile : csvFiles) {
            FileUtil.del(csvFile);
        }
        return inventoryItemList;
    }

    /**
     * 获取清单
     */
    public GenerateInventoryDTO generateInventory(LocalDate date) {
        // 初始化
        GenerateInventoryDTO generateInventoryDTO = initGenerateInventoryDTO(date);

        // 创建 OssInventory
        OssInventory inventory = createOssInvetory(generateInventoryDTO);
        generateInventoryDTO.setOssInventory(inventory);

        // 解析 OssInventoryItem
        List<OssInventoryItem> inventoryItemList = getOssInventoryItems(generateInventoryDTO);
        generateInventoryDTO.setOssInventoryItemList(inventoryItemList);

        return generateInventoryDTO;
    }


}