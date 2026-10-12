package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.model.OSSObject;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import java.util.List;

/** 交叉校验播放列表、manifest 和 OSS 实物；不允许越界对象或缺失分片。 */
@Service
@Slf4j
public class OutputValidator {
    @Resource private FileService fileService;

    public String validate(Transcode transcode, JSONObject manifest) {
        String playlistKey = manifest.getString("playlistKey");
        if (playlistKey == null || !playlistKey.equals(transcode.getM3u8Key())) {
            return "manifest playlistKey 与任务不符: " + playlistKey;
        }
        String m3u8Content = readObjectString(playlistKey);
        if (m3u8Content == null || m3u8Content.isBlank()) {
            return "playlist 不存在或为空: " + playlistKey;
        }
        if (!m3u8Content.contains("#EXT-X-ENDLIST")) {
            return "playlist 缺少 ENDLIST";
        }
        // 分片实物校验
        List<String> segmentNames = M3u8Util.getFilenames(m3u8Content);
        if (segmentNames.isEmpty()) {
            return "playlist 无分片";
        }
        String folder = FilenameUtils.getPath(playlistKey);
        for (String name : segmentNames) {
            if (!isSafeFilename(name)) return "非法分片路径";
            String key = folder + name;
            if (!ossObjectExists(key)) {
                return "分片缺失: " + name;
            }
        }
        // init segment（fMP4）
        String initUri = M3u8Util.getInitSegmentUri(m3u8Content);
        if (initUri != null && (!isSafeFilename(initUri) || !ossObjectExists(folder + initUri))) {
            return "init segment 缺失: " + initUri;
        }
        return validateManifestObjects(manifest, playlistKey, folder, segmentNames, initUri);
    }

    private String validateManifestObjects(JSONObject manifest, String playlistKey, String folder,
                                           List<String> segmentNames, String initUri) {
        var objects = manifest.getJSONArray("objects");
        if (objects != null) {
            java.util.Set<String> expected = new java.util.HashSet<>();
            expected.add(playlistKey);
            for (String name : segmentNames) expected.add(folder + name);
            if (initUri != null) expected.add(folder + initUri);
            java.util.Set<String> listed = new java.util.HashSet<>();
            for (int i = 0; i < objects.size(); i++) {
                JSONObject item = objects.getJSONObject(i);
                String key = item.getString("key");
                if (!expected.contains(key) || !listed.add(key)) return "manifest 对象越界或重复";
                try (OSSObject object = fileService.getObject(key)) {
                    if (object == null || item.getLongValue("size") <= 0
                            || object.getObjectMetadata().getContentLength() != item.getLongValue("size")) {
                        return "manifest 对象大小不符";
                    }
                } catch (Exception e) {
                    return "manifest 对象读取失败";
                }
            }
            if (!expected.equals(listed)) return "manifest 缺少引用对象";
        }
        return null;
    }

    private boolean isSafeFilename(String value) {
        return value != null && value.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*")
                && !value.contains("..");
    }

    private boolean ossObjectExists(String key) {
        try {
            OSSObject object = fileService.getObject(key);
            if (object == null) return false;
            long size = object.getObjectMetadata() == null ? 0 : object.getObjectMetadata().getContentLength();
            object.getObjectContent().close();
            return size > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readObjectString(String key) {
        try (OSSObject object = fileService.getObject(key)) {
            if (object == null || object.getObjectContent() == null) return null;
            return new String(object.getObjectContent().readAllBytes(), "UTF-8");
        } catch (Exception e) {
            log.warn("读取对象失败 key = {}: {}", key, e.getMessage());
            return null;
        }
    }
}
