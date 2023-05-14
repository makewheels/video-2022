package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.*;
import com.github.makewheels.video2022.etc.context.Context;
import com.github.makewheels.video2022.etc.context.RequestUtil;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.fileaccesslog.FileAccessLogService;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private AliyunOssService aliyunOssService;

    @Resource
    private FileRepository fileRepository;

    @Resource
    private FileAccessLogService fileAccessLogService;

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
        JSONObject credentials = aliyunOssService.getUploadCredentials(key);
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
        OSSObject object = aliyunOssService.getObject(key);
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
        return aliyunOssService.putObject(key, inputStream);
    }

    /**
     * 获取单个文件
     */
    public OSSObject getObject(String key) {
        return aliyunOssService.getObject(key);
    }

    /**
     * 获取多个文件信息
     * 因为阿里云没有批量查key接口，那就遍历一个一个查
     */
    public List<OSSObject> getObjects(List<String> keys) {
        List<OSSObject> objects = new ArrayList<>(keys.size());
        for (String key : keys) {
            aliyunOssService.getObject(key);
        }
        return objects;
    }

    /**
     * 按照prefix查找文件
     */
    public List<OSSObjectSummary> findObjects(String prefix) {
        return aliyunOssService.listAllObjects(prefix);
    }

    /**
     * 删除文件
     */
    public VoidResult deleteObject(String key) {
        return aliyunOssService.deleteObject(key);
    }

    /**
     * 删除文件
     */
    public List<String> deleteObjects(List<String> keys) {
        DeleteObjectsResult deleteObjectsResult = aliyunOssService.deleteObjects(keys);
        return deleteObjectsResult.getDeletedObjects();
    }

    /**
     * 预签名下载文件
     */
    public String generatePresignedUrl(String key, Duration duration) {
        return aliyunOssService.generatePresignedUrl(key, duration);
    }

    /**
     * 设置对象权限
     */
    public void setObjectAcl(String key, CannedAccessControlList cannedAccessControlList) {
        aliyunOssService.setObjectAcl(key, cannedAccessControlList);
    }

    /**
     * 改变object存储类型
     */
    public CopyObjectResult changeObjectStorageClass(String key, StorageClass storageClass) {
        return aliyunOssService.changeObjectStorageClass(key, storageClass);
    }

    /**
     * 取回object
     */
    public RestoreObjectResult restoreObject(String key) {
        return aliyunOssService.restoreObject(key);
    }

}
