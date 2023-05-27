package com.github.makewheels.video2022.file.md5;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 调用阿里云云函数，获取OSS文件的MD5
 * <p>
 * 示例请求：
 * {
 * "isCallbackEnable": false,
 * "objectList": [
 * {
 * "fileId": "646ea169aaac3166cd4e3594",
 * "key": "videos/64598761fab9341c488c38f7/646401931286d141d607bb22/original/646401931286d141d607bb22.mp4"
 * }
 * ]
 * }
 * <p>
 * 示例响应：
 * {
 * "aliyunRequestId": "1-64719513-28fa2dc08d98b49044f57cbf",
 * "objectList": [
 * {
 * "fileId": "646ea169aaac3166cd4e3594",
 * "key": "videos/64598761fab9341c488c38f7/646401931286d141d607bb22/original/646401931286d141d607bb22.mp4",
 * "md5": "458a3b2992784ad3e3b7a511d25d5752"
 * }
 * ]
 * }
 */
@Service
public class Md5CfService {
    @Resource
    private EnvironmentService environmentService;

    /**
     * 调用云函数
     */
    private JSONObject callCloudFunction(Map<String, Object> requestMap) {
        String url = environmentService.getAliyunCfUrlGetOssObjectMd5();
        String response = HttpUtil.post(url, JSON.toJSONString(requestMap));
        return JSONObject.parseObject(response);
    }

    /**
     * 解析objectList
     */
    private List<FileMd5DTO> getObjectList(JSONObject response) {
        return JSONArray.parseArray(
                JSON.toJSONString(response.getJSONArray("objectList")),
                FileMd5DTO.class);
    }

    public void getOssObjectMd5(FileMd5DTO fileMd5DTO) {
        // 组装请求参数
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("isCallbackEnable", false);
        requestMap.put("objectList", Lists.newArrayList(fileMd5DTO));

        // 调用云函数
        JSONObject response = callCloudFunction(requestMap);
        List<FileMd5DTO> fileMd5DTOList = getObjectList(response);

        // 设置md5返回
        String md5 = fileMd5DTOList.get(0).getMd5();
        fileMd5DTO.setMd5(md5);
    }

    public void getOssObjectMd5(List<FileMd5DTO> fileMd5DTOList) {
        // 组装请求参数
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("isCallbackEnable", false);
        requestMap.put("objectList", fileMd5DTOList);

        // 调用云函数
        JSONObject response = callCloudFunction(requestMap);

        // 设置md5返回
        Map<String, FileMd5DTO> key2ObjectMap = getObjectList(response).stream().collect(
                Collectors.toMap(FileMd5DTO::getKey, Function.identity()));
        for (FileMd5DTO fileMd5DTO : fileMd5DTOList) {
            FileMd5DTO object = key2ObjectMap.get(fileMd5DTO.getKey());
            fileMd5DTO.setMd5(object.getMd5());
        }
    }

}
