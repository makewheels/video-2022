package com.github.makewheels.video2022.transcode.aliyun;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.vod20170321.Client;
import com.aliyun.vod20170321.models.RegisterMediaRequest;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.video.bean.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

@Service
@Slf4j
public class AliyunVodService {
    @Resource
    private FileService fileService;

    public static Client createClient(String accessKeyId, String accessKeySecret) {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint("vod.cn-beijing.aliyuncs.com");
        try {
            return new Client(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void registerMedia(Video video) {
        Client client = createClient("accessKeyId", "accessKeySecret");
        RegisterMediaRequest request = new RegisterMediaRequest();
        JSONArray registerMetadatas = new JSONArray();
        JSONObject registerMetadata = new JSONObject();
        String fileURL = fileService.generatePresignedUrl(video.getOriginalFileKey(), Duration.ofHours(1));
        registerMetadata.put("FileURL", fileURL);
        registerMetadata.put("Title", video.getTitle());
        request.setRegisterMetadatas(registerMetadatas.toJSONString());
        try {
            client.registerMedia(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
