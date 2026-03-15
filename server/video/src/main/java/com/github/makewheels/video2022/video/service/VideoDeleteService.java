package com.github.makewheels.video2022.video.service;

import com.github.makewheels.video2022.cover.Cover;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.bean.TsFile;
import com.github.makewheels.video2022.oss.service.OssVideoService;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.watch.play.WatchLog;
import com.github.makewheels.video2022.comment.Comment;
import com.github.makewheels.video2022.comment.CommentLike;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class VideoDeleteService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private FileService fileService;
    @Resource
    private OssVideoService ossVideoService;

    /**
     * 删除视频及其所有关联数据
     */
    public void deleteVideo(String videoId) {
        Video video = videoRepository.getById(videoId);
        log.info("开始删除视频: videoId={}, title={}", videoId, video.getTitle());

        // 1. 从所有播放列表中移除该视频
        removeFromPlaylists(videoId);

        // 2. 删除观看记录
        long watchLogCount = mongoTemplate.remove(
                Query.query(Criteria.where("videoId").is(videoId)), WatchLog.class
        ).getDeletedCount();
        log.info("已删除 WatchLog: {} 条", watchLogCount);

        // 3. 删除心跳和播放进度记录
        deleteHeartbeatAndProgress(videoId);

        // 4. 删除转码记录和 TS 文件
        deleteTranscodes(videoId);

        // 5. 删除封面
        deleteCover(video.getCoverId());

        // 6. 删除原始文件（检查 MD5 引用）
        deleteRawFile(video.getRawFileId());

        // 7. 删除评论和评论点赞
        deleteComments(videoId);

        // 8. 删除视频点赞
        long videoLikeCount = mongoTemplate.remove(
                Query.query(Criteria.where("videoId").is(videoId)),
                com.github.makewheels.video2022.video.like.VideoLike.class
        ).getDeletedCount();
        log.info("已删除 VideoLike: {} 条", videoLikeCount);

        // 9. 删除 Video 记录
        mongoTemplate.remove(Query.query(Criteria.where("id").is(videoId)), Video.class);
        log.info("视频删除完成: videoId={}", videoId);
    }

    /**
     * 从所有播放列表中移除该视频
     */
    private void removeFromPlaylists(String videoId) {
        // 删除 PlayItem 记录
        long playItemCount = mongoTemplate.remove(
                Query.query(Criteria.where("videoId").is(videoId)), PlayItem.class
        ).getDeletedCount();
        log.info("已从播放列表移除 PlayItem: {} 条", playItemCount);

        // 更新 Playlist 的 videoList（移除 videoId）
        List<Playlist> playlists = mongoTemplate.find(
                Query.query(Criteria.where("videoList.videoId").is(videoId)), Playlist.class);
        for (Playlist playlist : playlists) {
            List<IdBean> videoList = playlist.getVideoList();
            if (videoList != null) {
                videoList.removeIf(idBean -> videoId.equals(idBean.getVideoId()));
                mongoTemplate.save(playlist);
            }
        }
        log.info("已更新 {} 个播放列表的 videoList", playlists.size());
    }

    /**
     * 删除心跳和播放进度记录
     */
    private void deleteHeartbeatAndProgress(String videoId) {
        try {
            // Heartbeat collection
            mongoTemplate.remove(
                    Query.query(Criteria.where("videoId").is(videoId)), "heartbeat");
            // Progress collection
            mongoTemplate.remove(
                    Query.query(Criteria.where("videoId").is(videoId)), "progress");
        } catch (Exception e) {
            log.warn("删除心跳/进度记录异常（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 删除转码记录和关联的 TS 文件
     */
    private void deleteTranscodes(String videoId) {
        List<Transcode> transcodes = mongoTemplate.find(
                Query.query(Criteria.where("videoId").is(videoId)), Transcode.class);

        List<String> ossKeysToDelete = new ArrayList<>();

        for (Transcode transcode : transcodes) {
            // 收集 M3U8 文件 key
            if (transcode.getM3u8Key() != null) {
                ossKeysToDelete.add(transcode.getM3u8Key());
            }

            // 删除 TS 文件记录并收集 OSS key
            if (transcode.getTsFileIds() != null) {
                for (String tsFileId : transcode.getTsFileIds()) {
                    TsFile tsFile = mongoTemplate.findById(tsFileId, TsFile.class);
                    if (tsFile != null && tsFile.getKey() != null) {
                        ossKeysToDelete.add(tsFile.getKey());
                    }
                }
                // 批量删除 TS 文件记录
                mongoTemplate.remove(
                        Query.query(Criteria.where("id").in(transcode.getTsFileIds())),
                        TsFile.class);
            }
        }

        // 删除 OSS 上的转码文件
        for (String key : ossKeysToDelete) {
            try {
                if (ossVideoService.doesObjectExist(key)) {
                    ossVideoService.deleteObject(key);
                }
            } catch (Exception e) {
                log.warn("删除 OSS 转码文件失败: key={}, error={}", key, e.getMessage());
            }
        }
        log.info("已删除转码文件: {} 个 OSS 对象", ossKeysToDelete.size());

        // 删除 Transcode 记录
        long transcodeCount = mongoTemplate.remove(
                Query.query(Criteria.where("videoId").is(videoId)), Transcode.class
        ).getDeletedCount();
        log.info("已删除 Transcode 记录: {} 条", transcodeCount);
    }

    /**
     * 删除封面
     */
    private void deleteCover(String coverId) {
        if (coverId == null) return;

        Cover cover = mongoTemplate.findById(coverId, Cover.class);
        if (cover == null) return;

        // 删除封面 OSS 文件
        if (cover.getKey() != null) {
            try {
                if (ossVideoService.doesObjectExist(cover.getKey())) {
                    ossVideoService.deleteObject(cover.getKey());
                }
            } catch (Exception e) {
                log.warn("删除封面 OSS 文件失败: key={}", cover.getKey());
            }
        }

        // 删除封面对应的 File 记录
        if (cover.getFileId() != null) {
            File coverFile = mongoTemplate.findById(cover.getFileId(), File.class);
            if (coverFile != null) {
                mongoTemplate.remove(coverFile);
            }
        }

        // 删除 Cover 记录
        mongoTemplate.remove(cover);
        log.info("已删除封面: coverId={}", coverId);
    }

    /**
     * 删除原始文件（检查 MD5 引用，避免删除被其他视频共享的 OSS 对象）
     */
    private void deleteRawFile(String rawFileId) {
        if (rawFileId == null) return;

        File file = mongoTemplate.findById(rawFileId, File.class);
        if (file == null) return;

        String key = file.getKey();
        String md5 = file.getMd5();

        // 检查是否有其他文件引用同一 MD5
        boolean hasOtherReferences = false;
        if (md5 != null) {
            long count = mongoTemplate.count(
                    Query.query(Criteria.where("md5").is(md5)
                            .and("id").ne(rawFileId)
                            .and("deleted").ne(true)),
                    File.class);
            hasOtherReferences = count > 0;
        }

        if (!hasOtherReferences && key != null) {
            try {
                if (ossVideoService.doesObjectExist(key)) {
                    ossVideoService.deleteObject(key);
                    log.info("已删除原始文件 OSS 对象: key={}", key);
                }
            } catch (Exception e) {
                log.warn("删除原始文件 OSS 对象失败: key={}", key);
            }
        } else if (hasOtherReferences) {
            log.info("原始文件有其他引用，跳过 OSS 删除: md5={}", md5);
        }

        // 删除 File 记录
        mongoTemplate.remove(file);
        log.info("已删除原始文件记录: fileId={}", rawFileId);
    }

    /**
     * 删除视频的所有评论及评论点赞
     */
    private void deleteComments(String videoId) {
        // 先查所有评论 ID，用于删除 CommentLike
        List<Comment> comments = mongoTemplate.find(
                Query.query(Criteria.where("videoId").is(videoId)), Comment.class);
        for (Comment comment : comments) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("commentId").is(comment.getId())),
                    CommentLike.class);
        }
        long commentCount = mongoTemplate.remove(
                Query.query(Criteria.where("videoId").is(videoId)), Comment.class
        ).getDeletedCount();
        log.info("已删除评论: {} 条", commentCount);
    }
}
