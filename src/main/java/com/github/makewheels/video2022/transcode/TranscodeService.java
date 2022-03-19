package com.github.makewheels.video2022.transcode;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.mps.v20190612.MpsClient;
import com.tencentcloudapi.mps.v20190612.models.*;
import org.springframework.stereotype.Service;

@Service
public class TranscodeService {
    private final Credential credential = new Credential(
            "AKIDqVv61h7IEvMXVGm22mHXXHm10kFUTDhv",
            "1mJbeRyHK7ewylIZbNt9AwTlNlunQq23");

    /**
     * 获取视频信息
     *
     * @param key
     * @return
     */
    public DescribeMediaMetaDataResponse describeMediaMetaData(String key) {
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("mps.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        MpsClient client = new MpsClient(credential, "ap-beijing", clientProfile);

        DescribeMediaMetaDataRequest req = new DescribeMediaMetaDataRequest();
        MediaInputInfo mediaInputInfo = new MediaInputInfo();
        mediaInputInfo.setType("COS");
        CosInputInfo cosInputInfo = new CosInputInfo();
        cosInputInfo.setBucket("video-2022-1253319037");
        cosInputInfo.setRegion("ap-beijing");
        cosInputInfo.setObject(key);
        mediaInputInfo.setCosInputInfo(cosInputInfo);

        req.setInputInfo(mediaInputInfo);

        DescribeMediaMetaDataResponse resp = null;
        try {
            resp = client.DescribeMediaMetaData(req);
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
        }
        return resp;
    }

    /**
     * 发起转码
     *
     * @param sourceKey
     * @param outputDir
     * @param resolution
     * @return
     */
    public ProcessMediaResponse processMedia(String sourceKey, String outputDir, String resolution) {
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("mps.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        MpsClient client = new MpsClient(credential, "ap-beijing", clientProfile);

        ProcessMediaRequest req = new ProcessMediaRequest();
        MediaInputInfo mediaInputInfo = new MediaInputInfo();
        mediaInputInfo.setType("COS");
        CosInputInfo cosInputInfo = new CosInputInfo();
        cosInputInfo.setBucket("video-2022-1253319037");
        cosInputInfo.setRegion("ap-beijing");
        cosInputInfo.setObject(sourceKey);
        mediaInputInfo.setCosInputInfo(cosInputInfo);

        req.setInputInfo(mediaInputInfo);

        req.setOutputDir(outputDir);
        MediaProcessTaskInput mediaProcessTaskInput = new MediaProcessTaskInput();

        TranscodeTaskInput[] transcodeTaskInputs = new TranscodeTaskInput[1];
        TranscodeTaskInput transcodeTaskInput = new TranscodeTaskInput();

        long templateId;
        if (resolution.equals(Resolution.R_1080P)) {
            templateId = 1225347L;
        } else if (resolution.equals(Resolution.R_720P)) {
            templateId = 1225348L;
        } else {
            templateId = 1225347L;
        }

        transcodeTaskInput.setDefinition(templateId);
        transcodeTaskInputs[0] = transcodeTaskInput;

        mediaProcessTaskInput.setTranscodeTaskSet(transcodeTaskInputs);

        req.setMediaProcessTask(mediaProcessTaskInput);

        // 返回的resp是一个ProcessMediaResponse的实例，与请求对象对应
        ProcessMediaResponse resp = null;
        try {
            resp = client.ProcessMedia(req);
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
        }
        return resp;
    }

    /**
     * 查询任务详情
     *
     * @param taskId
     */
    public DescribeTaskDetailResponse describeTaskDetail(String taskId) {
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("mps.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        MpsClient client = new MpsClient(credential, "ap-beijing", clientProfile);
        DescribeTaskDetailRequest req = new DescribeTaskDetailRequest();
        req.setTaskId(taskId);
        DescribeTaskDetailResponse resp = null;
        try {
            resp = client.DescribeTaskDetail(req);
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
        }
        return resp;
    }

}
