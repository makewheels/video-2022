package com.github.makewheels.video2022.playlist.item.request.move;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 移动视频在播放列表中的位置
 */
@Service
@Slf4j
public class MovePlayItemService {
    /**
     * 移动playItem
     */
    public void movePlayItem(MovePlayItemRequest movePlayItemRequest) {
        log.info("movePlayItem: {}", JSON.toJSONString(movePlayItemRequest));
    }
}
