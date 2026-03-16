package com.github.makewheels.video2022.notification;

import com.github.makewheels.video2022.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest extends BaseIntegrationTest {

    private static final String USER_A = "user_a";
    private static final String USER_B = "user_b";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void sendNotification_shouldSaveToDatabase() {
        Notification n = notificationService.sendNotification(
                NotificationType.COMMENT_REPLY, USER_A, USER_B,
                "回复了你的评论", "v_123", "c_456");

        assertNotNull(n);
        assertNotNull(n.getId());
        assertEquals(NotificationType.COMMENT_REPLY, n.getType());
        assertEquals(USER_A, n.getFromUserId());
        assertEquals(USER_B, n.getToUserId());
        assertEquals("回复了你的评论", n.getContent());
        assertEquals("v_123", n.getRelatedVideoId());
        assertEquals("c_456", n.getRelatedCommentId());
        assertFalse(n.isRead());
        assertNotNull(n.getCreateTime());
    }

    @Test
    void sendNotification_selfNotification_shouldReturnNull() {
        Notification n = notificationService.sendNotification(
                NotificationType.VIDEO_LIKE, USER_A, USER_A,
                "点赞了你的视频", "v_123", null);

        assertNull(n, "不应给自己发通知");
    }

    @Test
    void getMyNotifications_shouldReturnPaginated() {
        for (int i = 0; i < 25; i++) {
            notificationService.sendNotification(
                    NotificationType.VIDEO_LIKE, USER_A, USER_B,
                    "点赞了你的视频 " + i, "v_" + i, null);
        }

        NotificationPageVO page0 = notificationService.getMyNotifications(USER_B, 0, 10);
        assertEquals(10, page0.getList().size());
        assertEquals(25, page0.getTotal());
        assertEquals(3, page0.getTotalPages());
        assertEquals(0, page0.getCurrentPage());

        NotificationPageVO page2 = notificationService.getMyNotifications(USER_B, 2, 10);
        assertEquals(5, page2.getList().size());
    }

    @Test
    void getMyNotifications_orderedByCreateTimeDesc() {
        notificationService.sendNotification(
                NotificationType.VIDEO_LIKE, USER_A, USER_B, "第一条", "v_1", null);
        notificationService.sendNotification(
                NotificationType.COMMENT_REPLY, USER_A, USER_B, "第二条", "v_2", null);

        NotificationPageVO page = notificationService.getMyNotifications(USER_B, 0, 10);
        assertEquals(2, page.getList().size());
        assertTrue(page.getList().get(0).getCreateTime().compareTo(
                page.getList().get(1).getCreateTime()) >= 0,
                "应按创建时间倒序排列");
    }

    @Test
    void markAsRead_shouldUpdateNotification() {
        Notification n = notificationService.sendNotification(
                NotificationType.NEW_SUBSCRIBER, USER_A, USER_B,
                "关注了你", null, null);

        notificationService.markAsRead(USER_B, n.getId());

        Notification updated = notificationRepository.getById(n.getId());
        assertTrue(updated.isRead());
    }

    @Test
    void markAsRead_differentUser_shouldNotUpdate() {
        Notification n = notificationService.sendNotification(
                NotificationType.NEW_SUBSCRIBER, USER_A, USER_B,
                "关注了你", null, null);

        notificationService.markAsRead(USER_A, n.getId());

        Notification updated = notificationRepository.getById(n.getId());
        assertFalse(updated.isRead(), "其他用户不能标记别人的通知为已读");
    }

    @Test
    void markAllAsRead_shouldUpdateAllUnread() {
        notificationService.sendNotification(
                NotificationType.VIDEO_LIKE, USER_A, USER_B, "通知1", "v_1", null);
        notificationService.sendNotification(
                NotificationType.COMMENT_LIKE, USER_A, USER_B, "通知2", null, "c_1");
        notificationService.sendNotification(
                NotificationType.NEW_SUBSCRIBER, USER_A, USER_B, "通知3", null, null);

        assertEquals(3, notificationService.getUnreadCount(USER_B));

        notificationService.markAllAsRead(USER_B);

        assertEquals(0, notificationService.getUnreadCount(USER_B));
    }

    @Test
    void getUnreadCount_shouldReturnCorrectCount() {
        notificationService.sendNotification(
                NotificationType.VIDEO_LIKE, USER_A, USER_B, "通知1", "v_1", null);
        notificationService.sendNotification(
                NotificationType.COMMENT_REPLY, USER_A, USER_B, "通知2", "v_2", "c_1");

        assertEquals(2, notificationService.getUnreadCount(USER_B));
        assertEquals(0, notificationService.getUnreadCount(USER_A));

        Notification first = notificationService.getMyNotifications(USER_B, 0, 10)
                .getList().get(0);
        notificationService.markAsRead(USER_B, first.getId());

        assertEquals(1, notificationService.getUnreadCount(USER_B));
    }

    @Test
    void getUnreadCount_noNotifications_shouldReturnZero() {
        assertEquals(0, notificationService.getUnreadCount("nonexistent_user"));
    }

    @Test
    void sendAllNotificationTypes_shouldWork() {
        assertNotNull(notificationService.sendNotification(
                NotificationType.COMMENT_REPLY, USER_A, USER_B, "回复评论", "v_1", "c_1"));
        assertNotNull(notificationService.sendNotification(
                NotificationType.NEW_SUBSCRIBER, USER_A, USER_B, "新订阅", null, null));
        assertNotNull(notificationService.sendNotification(
                NotificationType.VIDEO_LIKE, USER_A, USER_B, "视频点赞", "v_1", null));
        assertNotNull(notificationService.sendNotification(
                NotificationType.COMMENT_LIKE, USER_A, USER_B, "评论点赞", null, "c_1"));

        assertEquals(4, notificationService.getUnreadCount(USER_B));
    }
}
