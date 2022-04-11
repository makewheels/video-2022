package com.github.makewheels.video2022.file;

import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.Protocol;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.http.HttpMethodName;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.BosObject;
import com.baidubce.services.bos.model.ObjectMetadata;
import com.baidubce.services.sts.StsClient;
import com.baidubce.services.sts.model.GetSessionTokenRequest;
import com.baidubce.services.sts.model.GetSessionTokenResponse;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.usermicroservice2022.response.ErrorCode;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.video.Provider;
import com.github.makewheels.video2022.video.VideoType;
import com.github.makewheels.video2022.video.YoutubeService;
import jdk.nashorn.internal.objects.NativeUint8Array;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
@Data
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private YoutubeService youtubeService;
    @Resource
    private AliyunOssService aliyunOssService;
    @Resource
    private BaiduBosService baiduBosService;

    @Value("${baidu.bos.accessBaseUrl}")
    private String baiduBosAccessBaseUrl;
    @Value("${baidu.bos.cdnBaseUrl}")
    private String baiduBosCdnBaseUrl;

    @Value("${aliyun.oss.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;
    @Value("${aliyun.oss.cdnBaseUrl}")
    private String aliyunOssCdnBaseUrl;

    /**
     * 新建视频时创建文件
     *
     * @param user
     * @param provider
     * @param requestBody
     * @return
     */
    public File create(User user, String provider, JSONObject requestBody) {
        File file = new File();
        file.setType(FileType.ORIGINAL_VIDEO);
        file.setUserId(user.getId());

        file.setProvider(provider);
        String videoType = requestBody.getString("type");
        file.setVideoType(videoType);

        //原始文件名和后缀
        if (videoType.equals(VideoType.USER_UPLOAD)) {
            String originalFilename = requestBody.getString("originalFilename");
            file.setOriginalFilename(originalFilename);
            file.setExtension(FilenameUtils.getExtension(originalFilename).toLowerCase());
        } else if (videoType.equals(VideoType.YOUTUBE)) {
            //YouTube搬运视频没有源文件名，只有拓展名，是yt-dlp给的，之后上传的key也会用这个拓展名
            String youtubeUrl = requestBody.getString("youtubeUrl");
            String youtubeVideoId = youtubeService.getYoutubeVideoId(youtubeUrl);
            String extension = youtubeService.getFileExtension(youtubeVideoId);
            file.setExtension(extension);
        }

        file.setStatus(FileStatus.CREATED);
        file.setCreateTime(new Date());
        mongoTemplate.save(file);
        return file;
    }

    /**
     * 根据provider获取url
     *
     * @param file
     * @return
     */
    public String getAccessUrl(File file) {
        String provider = file.getProvider();
        if (provider.equals(Provider.ALIYUN)) {
            return aliyunOssAccessBaseUrl + file.getKey();
        } else if (provider.equals(Provider.BAIDU)) {
            return baiduBosAccessBaseUrl + file.getKey();
        }
        return null;
    }

    /**
     * 根据provider获取url
     *
     * @param file
     * @return
     */
    public String getCdnUrl(File file) {
        String provider = file.getProvider();
        if (provider.equals(Provider.ALIYUN)) {
            return aliyunOssCdnBaseUrl + file.getKey();
        } else if (provider.equals(Provider.BAIDU)) {
            return baiduBosCdnBaseUrl + file.getKey();
        }
        return null;
    }

    /**
     * 获取上传凭证
     *
     * @param user
     * @param fileId
     * @return
     */
    public Result<JSONObject> getUploadCredentials(User user, String fileId) {
        File file = mongoTemplate.findById(fileId, File.class);
        //如果文件不存在，或者token找不到用户
        if (file == null || user == null) return Result.error(ErrorCode.FAIL);
        //如果上传文件不属于该用户
        if (!StringUtils.equals(user.getId(), file.getUserId()))
            return Result.error(ErrorCode.FAIL);
        String key = file.getKey();
        //根据provider，获取上传凭证
        String provider = file.getProvider();
        JSONObject credentials = null;
        if (provider.equals(Provider.BAIDU)) {
            credentials = baiduBosService.getUploadCredentials(key);
        } else if (provider.equals(Provider.ALIYUN)) {
            credentials = aliyunOssService.getUploadCredentials(key);
        }
        if (credentials == null) return Result.error(ErrorCode.FAIL);
        credentials.put("provider", provider);
        return Result.ok(credentials);
    }

    /**
     * 通知文件上传完成，和对象存储服务器确认，改变数据库File状态
     *
     * @param user
     * @param fileId
     * @return
     */
    public Result<Void> uploadFinish(User user, String fileId) {
        File file = mongoTemplate.findById(fileId, File.class);
        if (file == null) return Result.error(ErrorCode.FAIL);

        if (!StringUtils.equals(user.getId(), file.getUserId())) return Result.error(ErrorCode.FAIL);

        BosObject bosObject = baiduBosService.getObject(file.getKey());
        log.info("文件上传完成，fileId = " + fileId);
        log.info(JSONObject.toJSONString(bosObject));
        ObjectMetadata objectMetadata = bosObject.getObjectMetadata();
        file.setUploadTime(new Date());
        file.setSize(objectMetadata.getContentLength());
        file.setMd5(objectMetadata.getETag().toLowerCase());
        file.setStatus(FileStatus.READY);
        mongoTemplate.save(file);
        return Result.ok();
    }
}
