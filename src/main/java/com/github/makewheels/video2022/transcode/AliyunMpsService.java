package com.github.makewheels.video2022.transcode;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.Client;
import com.aliyun.mts20140618.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
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
                .setAccessKeyId(Base64.decodeStr(accessKeyId))
                .setAccessKeySecret(Base64.decodeStr(accessKeySecret));
        config.endpoint = "mts.cn-beijing.aliyuncs.com";
        config.protocol = "https";
        try {
            client = new Client(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return client;
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
        request.setAsync(false);
        SubmitMediaInfoJobResponse response = null;
        try {
            response = getClient().submitMediaInfoJob(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
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
        //阿里云转码的output特殊，
        //如果你给它 /6253dede8706fe314a47a54d
        //它会输出 /6253dede8706fe314a47a54d.m3u8 和 6253dede8706fe314a47a54d-00001.ts
        //所以这里把output的.m3u8去掉，这样也可以和百度兼容，输出都给.m3u8即可
        if (to.endsWith(".m3u8")) {
            to = to.replace(".m3u8", "");
        }
        JSONObject output = new JSONObject();
        output.put("OutputObject", URLUtil.encode(to));
        output.put("TemplateId", templateId);
        output.put("OutputLocation", "oss-cn-beijing");
        JSONArray outputs = new JSONArray();
        outputs.add(output);
        SubmitJobsRequest request = new SubmitJobsRequest()
                .setInput(getInput(from))
                .setOutputs(outputs.toJSONString())
                .setOutputBucket(bucket)
                .setOutputLocation("oss-cn-beijing")
                .setPipelineId("6c126c07a9b34a85b7093e7bfa9c3ad9");
        log.info("阿里云转码任务: from = " + from);
        log.info("阿里云转码任务: to = " + to);
        log.info("阿里云转码任务: SubmitJobsRequest = " + JSON.toJSONString(request));
        SubmitJobsResponse response = null;
        try {
            response = getClient().submitJobs(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("阿里云转码任务提交任务响应: " + JSON.toJSONString(response));
        return response;
    }

    /**
     * 创建转码任务
     */
    public SubmitJobsResponse createTranscodingJobByResolution(
            String sourceKey, String targetKey, String resolution) {
        if (resolution.equals(Resolution.R_1080P)) {
            return submitTranscodeJob(sourceKey, targetKey, "438e72fb70d04b89bf2b37b2769cf1ec");
        } else if (resolution.equals(Resolution.R_720P)) {
            return submitTranscodeJob(sourceKey, targetKey, "f96c8ccf81c44f079d285e13c1a1a104");
        }
        return null;
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

}
