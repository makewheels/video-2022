package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.transcode.aliyun;

import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.Client;
import com.aliyun.teaopenapi.models.Config;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import lombok.extern.slf4j.Slf4j;
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
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
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
     */
    private SubmitJobsResponse runSubmitTranscodeJob(String from, String to, String templateId) {
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
        SubmitJobsRequest request = new SubmitJobsRequest();
        request.setInput(getInput(from));
        request.setOutputs(outputs.toJSONString());
        request.setOutputBucket(bucket);
        request.setOutputLocation("oss-cn-beijing");
        request.setPipelineId("6c126c07a9b34a85b7093e7bfa9c3ad9");
        log.info("阿里云MPS转码任务: from = " + from);
        log.info("阿里云MPS转码任务: to = " + to);
        log.info("阿里云MPS转码任务: SubmitJobsRequest = " + JSON.toJSONString(request));
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
    public SubmitJobsResponse submitTranscodeJobByResolution(
            String sourceKey, String targetKey, String resolution) {
        switch (resolution) {
            case Resolution.R_480P:
                return runSubmitTranscodeJob(sourceKey, targetKey,
                        "6db7941bf7ec43c4a4ecc7f67d87ace6");
            case Resolution.R_720P:
                return runSubmitTranscodeJob(sourceKey, targetKey,
                        "f96c8ccf81c44f079d285e13c1a1a104");
            case Resolution.R_1080P:
                return runSubmitTranscodeJob(sourceKey, targetKey,
                        "438e72fb70d04b89bf2b37b2769cf1ec");
        }
        return null;
    }

    /**
     * 查询转码job
     */
    public QueryJobListResponse queryTranscodeJob(String jobIds) {
        log.info("查询阿里云转码作业, jobIds = " + jobIds);
        try {
            return getClient().queryJobList(new QueryJobListRequest().setJobIds(jobIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 提交截图作业
     */
    public SubmitSnapshotJobResponse submitSnapshotJob(String sourceKey, String targetKey) {
        SubmitSnapshotJobRequest request = new SubmitSnapshotJobRequest();
        request.setInput(getInput(sourceKey));

        //组装参数
        JSONObject outputFile = new JSONObject();
        outputFile.put("Bucket", bucket);
        outputFile.put("Location", "oss-cn-beijing");
        outputFile.put("Object", URLUtil.encode(targetKey));

        JSONObject snapshotConfig = new JSONObject();
        snapshotConfig.put("OutputFile", outputFile);
        snapshotConfig.put("Time", "0");
        snapshotConfig.put("Num", "1");
        request.setSnapshotConfig(snapshotConfig.toJSONString());
        request.setPipelineId("158c291025294f05b7d012a070ac8c28");

        //提交截图任务
        log.info("阿里云MPS提交截图任务：request = {}", JSON.toJSONString(request));
        try {
            return getClient().submitSnapshotJob(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 查询截帧job
     */
    public QuerySnapshotJobListResponse querySnapshotJob(String jobIds) {
        log.info("查询阿里云截帧作业，jobIds = " + jobIds);
        QuerySnapshotJobListRequest request = new QuerySnapshotJobListRequest();
        request.setSnapshotJobIds(jobIds);
        try {
            return getClient().querySnapshotJobList(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 简单查一个job
     */
    public QuerySnapshotJobListResponseBody.QuerySnapshotJobListResponseBodySnapshotJobListSnapshotJob
    simpleQueryOneJob(String jobId) {
        return querySnapshotJob(jobId).getBody().getSnapshotJobList().getSnapshotJob().get(0);
    }

}
