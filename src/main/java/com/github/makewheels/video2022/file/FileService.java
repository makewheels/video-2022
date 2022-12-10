package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.*;
import com.github.makewheels.video2022.context.Context;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.fileaccesslog.FileAccessLogService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.Video;
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
    public File createVideoFile(User user, Video video, JSONObject requestBody) {
        File file = new File();
        file.setType(FileType.ORIGINAL_VIDEO);
        file.setUserId(user.getId());

        file.setProvider(video.getProvider());
        String videoType = requestBody.getString("type");
        file.setVideoType(videoType);

        //原始文件名和后缀
        if (videoType.equals(VideoType.USER_UPLOAD)) {
            String originalFilename = requestBody.getString("originalFilename");
            file.setOriginalFilename(originalFilename);
            file.setExtension(FilenameUtils.getExtension(originalFilename).toLowerCase());
        } else if (videoType.equals(VideoType.YOUTUBE)) {
            //由于海外服务器获取拓展名太慢，所以移到后面的子线程中进行
            file.setExtension("webm");
        }

        file.setStatus(FileStatus.CREATED);
        file.setIsDeleted(false);
        file.setCreateTime(new Date());
        mongoTemplate.save(file);
        return file;
    }

    /**
     * 获取上传凭证
     */
    public Result<JSONObject> getUploadCredentials(User user, String fileId) {
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
    public Result<Void> uploadFinish(User user, String fileId) {
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
     * 列举所有文件
     */
    public List<OSSObjectSummary> listAllObjects(String prefix) {
        return aliyunOssService.listAllObjects(prefix);
    }

    /**
     * 删除文件
     */
    public List<String> deleteObjects(List<String> keys) {
        return aliyunOssService.deleteObjects(keys);
    }

    /**
     * 获取单个文件
     */
    public OSSObject getObject(String key) {
        return aliyunOssService.getObject(key);
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
        log.info("阿里云OSS设置对象权限, key = {}, cannedAccessControlList = {}",
                key, cannedAccessControlList);
        aliyunOssService.setObjectAcl(key, cannedAccessControlList);
    }

    /**
     * 上传文件
     */
    public PutObjectResult putObject(String key, InputStream inputStream) {
        PutObjectResult putObjectResult = aliyunOssService.putObject(key, inputStream);
        log.info("阿里云OSS上传文件: {}", JSON.toJSONString(putObjectResult));
        return putObjectResult;
    }

    /**
     * 访问文件：重定向到阿里云对象存储
     */
    public Result<Void> access(
            HttpServletRequest request, HttpServletResponse response, Context context,
            String resolution, String fileId, String timestamp, String nonce, String sign) {
        String videoId = context.getVideoId();
        String clientId = context.getClientId();
        String sessionId = context.getSessionId();

        File file = fileRepository.getById(fileId);

        //异步保存访问File记录
        new Thread(() -> fileAccessLogService.saveAccessLog(
                request, videoId, clientId, sessionId, resolution, fileId)).start();

        String url = generatePresignedUrl(file.getKey(), Duration.ofHours(3));
        response.setStatus(HttpServletResponse.SC_FOUND);
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.ok();
    }
}
