package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.StorageClass;
import com.github.makewheels.video2022.file.access.FileAccessLogService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.file.md5.FileMd5DTO;
import com.github.makewheels.video2022.file.md5.Md5CfService;
import com.github.makewheels.video2022.oss.service.OssVideoService;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.context.Context;
import com.github.makewheels.video2022.system.context.RequestUtil;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.utils.IdService;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
@Slf4j
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private OssVideoService ossVideoService;
    @Resource
    private FileRepository fileRepository;
    @Resource
    private TsFileRepository tsFileRepository;
    @Resource
    private FileAccessLogService fileAccessLogService;
    @Resource
    private Md5CfService md5CfService;
    @Resource
    private IdService idService;

    /**
     * 新建视频时创建文件
     */
    public File createVideoFile(CreateVideoDTO createVideoDTO) {
        File file = new File();
        file.setId(idService.getFileId());
        file.setFileType(FileType.RAW_VIDEO);
        file.setUploaderId(createVideoDTO.getUser().getId());

        file.setProvider(createVideoDTO.getVideo().getProvider());
        String videoType = createVideoDTO.getVideoType();
        file.setVideoType(videoType);

        //原始文件名和后缀
        if (videoType.equals(VideoType.USER_UPLOAD)) {
            String rawFilename = createVideoDTO.getRawFilename();
            file.setRawFilename(rawFilename);
            file.setExtension(FilenameUtils.getExtension(rawFilename).toLowerCase());
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
    public JSONObject getUploadCredentials(String fileId) {
        File file = fileRepository.getById(fileId);
        JSONObject credentials = ossVideoService.generateUploadCredentials(file.getKey());
        if (credentials == null) {
            throw new VideoException(ErrorCode.FILE_GENERATE_UPLOAD_CREDENTIALS_FAIL);
        }
        credentials.put("provider", file.getProvider());
        log.info("生成上传凭证, fileId = " + fileId + ", " + JSON.toJSONString(credentials));
        return credentials;
    }

    /**
     * 通知文件上传完成，和对象存储服务器确认，改变数据库File状态
     */
    public void uploadFinish(String fileId) {
        File file = fileRepository.getById(fileId);
        String key = file.getKey();
        log.info("FileService 处理文件上传完成, fileId = " + fileId + ", key = " + key);
        OSSObject object = ossVideoService.getObject(key);
        ObjectMetadata objectMetadata = object.getObjectMetadata();
        file.setSize(objectMetadata.getContentLength());
        file.setEtag(objectMetadata.getETag());
        file.setUploadTime(objectMetadata.getLastModified());
        file.setFileStatus(FileStatus.READY);
        mongoTemplate.save(file);
    }

    /**
     * 访问文件：重定向到阿里云对象存储
     */
    public void access(Context context, String resolution, String fileId, String timestamp,
                       String nonce, String sign) {
        String videoId = context.getVideoId();
        String clientId = context.getClientId();
        String sessionId = context.getSessionId();

        // 异步处理访问File记录
        HttpServletRequest request = RequestUtil.getRequest();
        new Thread(() -> fileAccessLogService.handleAccessLog(
                request, videoId, clientId, sessionId, resolution, fileId))
                .start();

        // 设置返回结果
        String key = tsFileRepository.getKeyById(fileId);
        String url = generatePresignedUrl(key, Duration.ofHours(3));
        HttpServletResponse response = RequestUtil.getResponse();
        response.setStatus(302);
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * 通过id获取对象存储的key
     */
    public String getKeyByFileId(String fileId) {
        File file = fileRepository.getById(fileId);
        if (file == null) return null;
        return file.getKey();
    }

    /**
     * 获取单个文件
     */
    public OSSObject getObject(String key) {
        return ossVideoService.getObject(key);
    }

    /**
     * 按照prefix查找文件
     */
    public List<OSSObjectSummary> findObjects(String prefix) {
        return ossVideoService.listAllObjects(prefix);
    }

    /**
     * 对象存储文件key是否存在
     */
    public boolean doesOSSObjectExist(String key) {
        return ossVideoService.doesObjectExist(key);
    }

    /**
     * 获取文件的md5
     */
    public String getMd5(File file) {
        FileMd5DTO fileMd5DTO = new FileMd5DTO();
        fileMd5DTO.setFileId(file.getId());
        fileMd5DTO.setKey(file.getKey());
        md5CfService.getOssObjectMd5(fileMd5DTO);
        return fileMd5DTO.getMd5();
    }

    /**
     * 删除文件
     */
    public void deleteFile(File file) {
        log.info("FileService 删除文件，fileId = " + file.getId() + ", key = " + file.getKey());
        ossVideoService.deleteObject(file.getKey());
        file.setDeleted(true);
        file.setDeleteTime(new Date());
        file.setUpdateTime(new Date());
        mongoTemplate.save(file);
    }

    /**
     * 预签名下载文件
     */
    public String generatePresignedUrl(String key, Duration duration) {
        return ossVideoService.generatePresignedUrl(key, duration);
    }

    /**
     * 批量预签名下载文件
     */
    public Map<String, String> generatePresignedUrl(List<String> keyList, Duration duration) {
        Map<String, String> map = new HashMap<>(keyList.size());
        for (String key : keyList) {
            String url = ossVideoService.generatePresignedUrl(key, duration);
            map.put(key, url);
        }
        return map;
    }

    /**
     * 改变对象权限
     */
    public void changeObjectAcl(String fileId, String acl) {
        File file = fileRepository.getById(fileId);
        file.setAcl(acl);
        ossVideoService.setObjectAcl(file.getKey(), CannedAccessControlList.parse(acl));
        mongoTemplate.save(file);
    }

    /**
     * 改变存储类型
     */
    public void changeStorageClass(String fileId, String storageClass) {
        File file = fileRepository.getById(fileId);
        file.setStorageClass(storageClass);
        ossVideoService.changeObjectStorageClass(file.getKey(), StorageClass.parse(storageClass));
        mongoTemplate.save(file);
    }

}
