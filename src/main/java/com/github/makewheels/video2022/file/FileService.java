package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.*;
import com.github.makewheels.video2022.file.access.FileAccessLogService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.md5.FileMd5DTO;
import com.github.makewheels.video2022.file.md5.Md5CfService;
import com.github.makewheels.video2022.file.oss.OssService;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private OssService ossService;

    @Resource
    private FileRepository fileRepository;

    @Resource
    private FileAccessLogService fileAccessLogService;

    @Resource
    private Md5CfService md5CfService;

    /**
     * 新建视频时创建文件
     */
    public File createVideoFile(CreateVideoDTO createVideoDTO) {
        File file = new File();
        file.setType(FileType.ORIGINAL_VIDEO);
        file.setUserId(createVideoDTO.getUser().getId());

        file.setProvider(createVideoDTO.getVideo().getProvider());
        String videoType = createVideoDTO.getVideoType();
        file.setVideoType(videoType);

        //原始文件名和后缀
        if (videoType.equals(VideoType.USER_UPLOAD)) {
            String originalFilename = createVideoDTO.getOriginalFilename();
            file.setOriginalFilename(originalFilename);
            file.setExtension(FilenameUtils.getExtension(originalFilename).toLowerCase());
        } else if (videoType.equals(VideoType.YOUTUBE)) {
            //由于海外服务器获取拓展名太慢，所以移到后面的子线程中进行
            file.setExtension("webm");
        }

        mongoTemplate.save(file);
        return file;
    }

    /**
     * 获取上传凭证
     */
    public Result<JSONObject> getUploadCredentials(String fileId) {
        User user = UserHolder.get();
        File file = fileRepository.getById(fileId);
        //如果文件不存在，或者token找不到用户
        if (file == null || user == null) return Result.error(ErrorCode.FAIL);
        //如果上传文件不属于该用户
        if (!StringUtils.equals(user.getId(), file.getUserId())) return Result.error(ErrorCode.FAIL);
        String key = file.getKey();
        //根据provider，获取上传凭证
        String provider = file.getProvider();
        JSONObject credentials = ossService.getUploadCredentials(key);
        if (credentials == null) return Result.error(ErrorCode.FAIL);
        credentials.put("provider", provider);
        log.info("生成上传凭证，fileId = " + fileId + " " + JSON.toJSONString(credentials));
        return Result.ok(credentials);
    }

    /**
     * 通知文件上传完成，和对象存储服务器确认，改变数据库File状态
     */
    public Result<Void> uploadFinish(String fileId) {
        User user = UserHolder.get();
        File file = fileRepository.getById(fileId);
        if (file == null) return Result.error(ErrorCode.FAIL);
        if (!StringUtils.equals(user.getId(), file.getUserId())) return Result.error(ErrorCode.FAIL);

        String key = file.getKey();
        log.info("处理文件上传完成，fileId = " + fileId + ", key = " + key);

        //判断provider
        OSSObject object = ossService.getObject(key);
        ObjectMetadata objectMetadata = object.getObjectMetadata();
        file.setSize(objectMetadata.getContentLength());
        file.setEtag(objectMetadata.getETag());

        file.setUploadTime(new Date());
        file.setStatus(FileStatus.READY);
        mongoTemplate.save(file);
        return Result.ok();
    }

    /**
     * 访问文件：重定向到阿里云对象存储
     */
    public Result<Void> access(
            Context context, String resolution, String fileId, String timestamp, String nonce, String sign) {
        HttpServletRequest request = RequestUtil.getRequest();
        HttpServletResponse response = RequestUtil.getResponse();

        String videoId = context.getVideoId();
        String clientId = context.getClientId();
        String sessionId = context.getSessionId();


        //异步保存访问File记录
        new Thread(() -> fileAccessLogService.saveAccessLog(
                request, videoId, clientId, sessionId, resolution, fileId)).start();

        String key = getKey(fileId);
        String url = generatePresignedUrl(key, Duration.ofHours(3));
        response.setStatus(HttpServletResponse.SC_FOUND);
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.ok();
    }

    /**
     * 通过id获取对象存储的key
     */
    public String getKey(String fileId) {
        File file = fileRepository.getById(fileId);
        return file.getKey();
    }

    /**
     * 上传文件
     */
    public PutObjectResult putObject(String key, InputStream inputStream) {
        return ossService.putObject(key, inputStream);
    }

    /**
     * 获取单个文件
     */
    public OSSObject getObject(String key) {
        return ossService.getObject(key);
    }

    /**
     * 获取多个文件信息
     * 因为阿里云没有批量查key接口，那就遍历一个一个查
     */
    public List<OSSObject> getObjects(List<String> keys) {
        List<OSSObject> objects = new ArrayList<>(keys.size());
        for (String key : keys) {
            ossService.getObject(key);
        }
        return objects;
    }

    /**
     * 按照prefix查找文件
     */
    public List<OSSObjectSummary> findObjects(String prefix) {
        return ossService.listAllObjects(prefix);
    }

    /**
     * 获取文件的md5
     */
    public String getMd5(String fileId) {
        File file = fileRepository.getById(fileId);
        FileMd5DTO fileMd5DTO = new FileMd5DTO();
        fileMd5DTO.setFileId(fileId);
        fileMd5DTO.setKey(file.getKey());
        return md5CfService.getOssObjectMd5(fileMd5DTO);
    }

    /**
     * 批量获取文件的md5
     */
//    public String getMd5(List<String> keyList) {
//        return md5CfService.getOssObjectMd5(keyList);
//    }

    /**
     * 删除文件
     */
    public VoidResult deleteObject(String key) {
        return ossService.deleteObject(key);
    }

    /**
     * 删除文件
     */
    public List<String> deleteObjects(List<String> keys) {
        DeleteObjectsResult deleteObjectsResult = ossService.deleteObjects(keys);
        return deleteObjectsResult.getDeletedObjects();
    }

    /**
     * 预签名下载文件
     */
    public String generatePresignedUrl(String key, Duration duration) {
        return ossService.generatePresignedUrl(key, duration);
    }

    /**
     * 批量预签名下载文件
     */
    public Map<String, String> generatePresignedUrl(List<String> keyList, Duration duration) {
        Map<String, String> map = new HashMap<>(keyList.size());
        for (String key : keyList) {
            String url = ossService.generatePresignedUrl(key, duration);
            map.put(key, url);
        }
        return map;
    }

    /**
     * 设置对象权限
     */
    public void setObjectAcl(String key, CannedAccessControlList cannedAccessControlList) {
        ossService.setObjectAcl(key, cannedAccessControlList);
    }

    /**
     * 改变object存储类型
     */
    public CopyObjectResult changeObjectStorageClass(String key, StorageClass storageClass) {
        return ossService.changeObjectStorageClass(key, storageClass);
    }

    /**
     * 取回object
     */
    public RestoreObjectResult restoreObject(String key) {
        return ossService.restoreObject(key);
    }

}
