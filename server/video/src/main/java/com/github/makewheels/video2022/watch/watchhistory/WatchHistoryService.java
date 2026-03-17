package com.github.makewheels.video2022.watch.watchhistory;

import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.watch.play.WatchLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WatchHistoryService {
    @Resource
    private WatchHistoryRepository watchHistoryRepository;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CoverService coverService;

    public Result<Map<String, Object>> getMyHistory(String userId, int page, int pageSize) {
        int skip = page * pageSize;
        List<WatchLog> watchLogs = watchHistoryRepository.findByViewerId(userId, skip, pageSize);
        long total = watchHistoryRepository.countByViewerId(userId);

        List<String> videoIds = watchLogs.stream()
                .map(WatchLog::getVideoId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Video> videoMap = videoRepository.getMapByIdList(videoIds);

        List<WatchHistoryItem> items = new ArrayList<>();
        for (WatchLog watchLog : watchLogs) {
            WatchHistoryItem item = new WatchHistoryItem();
            item.setVideoId(watchLog.getVideoId());
            item.setWatchTime(watchLog.getCreateTime());

            Video video = videoMap.get(watchLog.getVideoId());
            if (video != null) {
                item.setTitle(video.getTitle());
                if (video.getCoverId() != null) {
                    item.setCoverUrl(coverService.getSignedCoverUrl(video.getCoverId()));
                }
            }
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return Result.ok(result);
    }

    public Result<Void> clearHistory(String userId) {
        watchHistoryRepository.deleteByViewerId(userId);
        log.info("清除观看历史: userId = {}", userId);
        return Result.ok();
    }
}
