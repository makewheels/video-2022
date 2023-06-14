package com.github.makewheels.video2022.redis;

import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 通用缓存类，为解决循环依赖而生
 * 先从redis获取，再从mongo获取
 */
@Service
public class CacheService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private RedisService redisService;

    /**
     * 获取redis的key
     */
    private String getRedisKey(Class<?> clazz, String id) {
        if (clazz.equals(Video.class)) {
            return RedisKey.videoCache(id);
        } else if (clazz.equals(Transcode.class)) {
            return RedisKey.transcodeCache(id);
        } else if (clazz.equals(User.class)) {
            return RedisKey.userCache(id);
        } else if (clazz.equals(Playlist.class)) {
            return RedisKey.playlistCache(id);
        } else if (clazz.equals(PlayItem.class)) {
            return RedisKey.playlistItemCache(id);
        }
        return null;
    }

    public void updateVideo(Video video) {
        String id = video.getId();
        if (id == null) return;

        video.setUpdateTime(new Date());

        String redisKey = getRedisKey(Video.class, id);
        redisService.del(redisKey);

        mongoTemplate.save(video);
    }

}
