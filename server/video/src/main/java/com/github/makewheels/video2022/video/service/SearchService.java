package com.github.makewheels.video2022.video.service;

import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.cover.CoverService;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.bean.entity.YouTube;
import com.github.makewheels.video2022.video.bean.vo.SearchResultVO;
import com.github.makewheels.video2022.video.bean.vo.VideoVO;
import com.github.makewheels.video2022.video.constants.VideoType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchService {
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private CoverService coverService;
    @Resource
    private UserRepository userRepository;

    public Result<SearchResultVO> search(String keyword, String category, int page, int pageSize) {
        List<Video> videos = videoRepository.searchPublicVideos(keyword, category, page * pageSize, pageSize);
        long total = videoRepository.countSearchPublicVideos(keyword, category);

        // 获取封面url
        List<String> coverIdList = videos.stream()
                .map(Video::getCoverId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<String, String> coverId2UrlMap = coverService.getSignedCoverUrl(coverIdList);

        // 获取上传者信息
        Map<String, User> uploaderMap = buildUploaderMap(videos);

        List<VideoVO> videoVOList = new ArrayList<>(videos.size());
        for (Video video : videos) {
            VideoVO videoVO = new VideoVO();
            BeanUtils.copyProperties(video, videoVO);
            videoVO.setCoverUrl(coverId2UrlMap.get(video.getCoverId()));
            videoVO.setCreateTimeString(DateUtil.formatDateTime(video.getCreateTime()));

            User uploader = uploaderMap.get(video.getUploaderId());
            if (uploader != null) {
                videoVO.setUploaderName(getUploaderDisplayName(uploader));
                videoVO.setUploaderAvatarUrl(uploader.getAvatarUrl());
                videoVO.setUploaderId(uploader.getId());
            } else {
                videoVO.setUploaderName("未知用户");
            }
            mapVideoNestedFields(videoVO, video);
            videoVOList.add(videoVO);
        }

        SearchResultVO searchResultVO = new SearchResultVO();
        searchResultVO.setContent(videoVOList);
        searchResultVO.setTotal(total);
        searchResultVO.setTotalPages((int) Math.ceil((double) total / pageSize));
        searchResultVO.setCurrentPage(page);
        searchResultVO.setPageSize(pageSize);
        return Result.ok(searchResultVO);
    }

    private String getUploaderDisplayName(User user) {
        String displayName = user.getNickname();
        if (displayName == null || displayName.isEmpty()) {
            String phone = user.getPhone();
            if (phone != null && phone.length() >= 7) {
                displayName = phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
            } else {
                displayName = "未知用户";
            }
        }
        return displayName;
    }

    private Map<String, User> buildUploaderMap(List<Video> videos) {
        Set<String> uploaderIds = videos.stream()
                .map(Video::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> uploaderMap = new HashMap<>();
        for (String uploaderId : uploaderIds) {
            User user = userRepository.getById(uploaderId);
            if (user != null) {
                uploaderMap.put(uploaderId, user);
            }
        }
        return uploaderMap;
    }

    private void mapVideoNestedFields(VideoVO videoVO, Video video) {
        if (video.getWatch() != null) {
            videoVO.setWatchCount(video.getWatch().getWatchCount());
            videoVO.setWatchId(video.getWatch().getWatchId());
            videoVO.setWatchUrl(video.getWatch().getWatchUrl());
            videoVO.setShortUrl(video.getWatch().getShortUrl());
        }
        if (video.getMediaInfo() != null) {
            videoVO.setDuration(video.getMediaInfo().getDuration());
        }
        if (VideoType.YOUTUBE.equals(video.getVideoType()) && video.getYouTube() != null) {
            YouTube youTube = video.getYouTube();
            if (youTube.getPublishTime() != null) {
                videoVO.setYoutubePublishTimeString(DateUtil.formatDateTime(youTube.getPublishTime()));
            }
        }
    }
}
