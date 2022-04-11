package com.github.makewheels.video2022.transcode;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.Client;
import com.aliyun.mts20140618.models.*;
import com.aliyun.teaopenapi.models.Config;
import jdk.nashorn.internal.scripts.JO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AliyunMpsService {
    @Value("${aliyun.oss.bucket}")
    private String bucket;

    @Value("${aliyun.mps.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.mps.secretKey}")
    private String accessKeySecret;

    private Client client;

    private Client getClient() {
        if (client != null) return client;
        Config config = new Config()
                .setAccessKeyId(Base64.decodeStr("TFRBSTV0UmdoeGdTNkJEMlYzN3BiWXUy"))
                .setAccessKeySecret(Base64.decodeStr("Tkk0ejVrS0J4UFlMMXFQNmFmRHJONjlmQUFDZE5p"));
//                .setAccessKeyId(Base64.decodeStr(accessKeyId))
//                .setAccessKeySecret(Base64.decodeStr(accessKeySecret));
        config.endpoint = "mts.cn-beijing.aliyuncs.com";
        config.protocol = "https";
        try {
            return new Client(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getInput(String key) {
        JSONObject input = new JSONObject();
        input.put("Bucket", bucket);
        input.put("Location", "oss-cn-beijing");
        input.put("Object", URLUtil.encode(key));
        return input.toJSONString();
    }

    /**
     * 获取媒体信息
     *
     * @param key
     * @return
     */
    public SubmitMediaInfoJobResponse getMediaInfo(String key) {
        SubmitMediaInfoJobRequest request = new SubmitMediaInfoJobRequest();
        request.setInput(getInput(key));
        SubmitMediaInfoJobResponse response = null;
        try {
            response = getClient().submitMediaInfoJob(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
//        String body = JSON.toJSONString(response.getBody());
//        JSONObject jsonObject = JSON.parseObject(body);
//        JSONObject properties = jsonObject.getJSONObject("mediaInfoJob").getJSONObject("properties");
//        int width = Integer.parseInt(properties.getString("width"));
//        int height = Integer.parseInt(properties.getString("height"));
//        int duration = (int) (Double.parseDouble(properties.getString("duration")) * 1000);
    }

    /**
     * 提交转码任务
     *
     * @param from
     * @param to
     * @param templateId
     * @return
     */
    public SubmitJobsResponse submitTranscodeJob(String from, String to, String templateId) {
        SubmitJobsRequest request = new SubmitJobsRequest()
                .setInput(getInput(from))
                .setOutputs("[{\"OutputObject\":\"" + URLUtil.encode(to) + "\",\"" +
                        "TemplateId\":\"" + templateId + "\"}]")
                .setOutputBucket(bucket)
                .setPipelineId("6c126c07a9b34a85b7093e7bfa9c3ad9");
        log.info("阿里云转码任务: from = " + from);
        log.info("阿里云转码任务: to = " + to);
        log.info("submitJobsRequest = " + JSON.toJSONString(request));
        SubmitJobsResponse response = null;
        try {
            response = getClient().submitJobs(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("阿里云转码任务提交任务相应: SubmitJobsResponse = " + JSON.toJSONString(response));
        return response;
    }

    /**
     * 查询转码作业
     *
     * @param jobIds
     * @return
     */
    public QueryJobListResponse queryJob(String jobIds) {
        log.info("阿里云查询转码作业，jobIds = " + jobIds);
        try {
            return getClient().queryJobList(new QueryJobListRequest().setJobIds(jobIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 是否是已结束的状态
     *
     * @param jobState
     * @return
     */
    private boolean isFinishedStatus(String jobState) {
        return StringUtils.equalsAny(jobState, AliyunTranscodeStatus.TranscodeSuccess,
                AliyunTranscodeStatus.TranscodeFail, AliyunTranscodeStatus.TranscodeCancelled);
    }

}
