package com.github.makewheels.video2022.notification;

import lombok.Data;

import java.util.List;

@Data
public class NotificationPageVO {
    private List<Notification> list;
    private long total;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
