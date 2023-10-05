package com.github.makewheels.video2022.oss.inventory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.compress.CompressUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.etc.springboot.exception.VideoException;
import com.github.makewheels.video2022.oss.OssDataService;
import com.github.makewheels.video2022.oss.OssVideoService;
import com.github.makewheels.video2022.oss.inventory.bean.GenerateInventoryDTO;
import com.github.makewheels.video2022.oss.inventory.bean.OssInventory;
import com.github.makewheels.video2022.oss.inventory.bean.OssInventoryItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import javax.annotation.Resource;

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
    @Resource
    private OssVideoService ossVideoService;

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
        String manifestKey = ossObjectSummary.getKey();
        log.info("获取清单GZ压缩文件的key, manifestKey = " + manifestKey);
        return manifestKey;
    }

    /**
     * 解析清单文件，获取清单GZ压缩文件的key
     * {
     *     "creationTimestamp":"1694448644",
     *     "destinationBucket":"oss-data-bucket",
     *     "fileFormat":"CSV",
     *     "fileSchema":"Bucket, Key, Size, StorageClass, LastModifiedDate, ETag, IsMultipartUploaded, EncryptionStatus",
     *     "files":[
     *         {
     *             "MD5checksum":"5FC695B803A384A2FAA01D747C405FD1",
     *             "key":"video-2022-dev/inventory/video-2022-dev/inventory-rule/data/fc581f25-2ab5-4f11-a88a-5a74ec15241f.csv.gz",
     *             "size":12586
     *         }
     *     ],
     *     "sourceBucket":"video-2022-dev",
     *     "version":"2019-09-01"
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
    public List<OssInventoryItem> parseFileToInventory(File file) {
        log.info("解析CSV " + file.getAbsolutePath());
        CsvData data = CsvUtil.getReader().read(file);
        List<OssInventoryItem> inventoryList = new ArrayList<>(data.getRowCount());
        for (CsvRow row : data.getRows()) {
            OssInventoryItem inventoryItem = new OssInventoryItem();
            inventoryItem.setBucketName(row.get(0));
            inventoryItem.setObjectName(URLUtil.decode(row.get(1)));
            inventoryItem.setSize(Long.parseLong(row.get(2)));
            inventoryItem.setStorageClass(row.get(3));
            inventoryItem.setLastModifiedDate(DateUtil.parse(row.get(4), "yyyy-MM-dd'T'HH-mm-ss'Z'"));
            inventoryItem.setETag(row.get(5));
            inventoryItem.setIsMultipartUploaded(Boolean.parseBoolean(row.get(6)));
            inventoryItem.setEncryptionStatus(Boolean.parseBoolean(row.get(7)));
            inventoryList.add(inventoryItem);
        }
        log.info("总共解析出inventoryList.size = " + inventoryList.size());
        return inventoryList;
    }

    /**
     * 获取清单
     */
    public GenerateInventoryDTO getGenerateInventoryDTO(Date date) {
        // 获取快照，解析出inventoryItemList
        String manifestKey = this.getManifestKey(date);
        JSONObject manifest = JSON.parseObject(ossDataService.getObjectContent(manifestKey));
        List<String> gzFileKeys = this.getInventoryGzFileKeys(manifest);
        List<File> csvFiles = this.getCsvFiles(gzFileKeys);
        List<OssInventoryItem> inventoryItemList = new ArrayList<>();
        for (File csvFile : csvFiles) {
            inventoryItemList.addAll(this.parseFileToInventory(csvFile));
            FileUtil.del(csvFile);
        }

        // 创建 inventory
        OssInventory inventory = new OssInventory();
        inventory.setSnapshotSourceBucket(manifest.getString("sourceBucket"));
        inventory.setInventoryStorageBucket(ossDataService.getBucket());
        inventory.setGzOssKeys(gzFileKeys);
        inventory.setManifestKey(manifestKey);
        inventory.setManifest(manifest);
        long creationTimestampInMillis = Long.parseLong(manifest.getString("creationTimestamp")) * 1000;
        inventory.setAliyunGenerationTime(new Date(creationTimestampInMillis));

        // 返回 GenerateInventoryDTO
        GenerateInventoryDTO generateInventoryDTO = new GenerateInventoryDTO();
        generateInventoryDTO.setOssInventory(inventory);
        generateInventoryDTO.setOssInventoryItemList(inventoryItemList);
        return generateInventoryDTO;
    }

}