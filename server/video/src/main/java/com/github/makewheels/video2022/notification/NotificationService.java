package com.github.makewheels.video2022.notification;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class NotificationService {
    @Resource
    private NotificationRepository notificationRepository;

    /**
     * 发送通知
     */
    public Notification sendNotification(String type, String fromUserId, String toUserId,
                                         String content, String relatedVideoId,
                                         String relatedCommentId) {
        // 不给自己发通知
        if (fromUserId != null && fromUserId.equals(toUserId)) {
            return null;
        }

        Notification notification = new Notification();
        notification.setType(type);
        notification.setFromUserId(fromUserId);
        notification.setToUserId(toUserId);
        notification.setContent(content);
        notification.setRelatedVideoId(relatedVideoId);
        notification.setRelatedCommentId(relatedCommentId);
        notification.setRead(false);
        notification.setCreateTime(new Date());

        notificationRepository.save(notification);
        log.info("发送通知: type={}, from={}, to={}", type, fromUserId, toUserId);
        return notification;
    }

    /**
     * 获取我的通知（分页）
     */
    public NotificationPageVO getMyNotifications(String userId, int page, int pageSize) {
        int skip = page * pageSize;
        List<Notification> list = notificationRepository.getByToUserId(userId, skip, pageSize);
        long total = notificationRepository.countByToUserId(userId);

        NotificationPageVO vo = new NotificationPageVO();
        vo.setList(list);
        vo.setTotal(total);
        vo.setCurrentPage(page);
        vo.setPageSize(pageSize);
        vo.setTotalPages((int) Math.ceil((double) total / pageSize));
        return vo;
    }

    /**
     * 标记单条已读
     */
    public void markAsRead(String userId, String notificationId) {
        notificationRepository.markAsRead(userId, notificationId);
        log.info("标记已读: userId={}, notificationId={}", userId, notificationId);
    }

    /**
     * 标记全部已读
     */
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
        log.info("标记全部已读: userId={}", userId);
    }

    /**
     * 获取未读数量
     */
    public int getUnreadCount(String userId) {
        return (int) notificationRepository.countUnread(userId);
    }
}
