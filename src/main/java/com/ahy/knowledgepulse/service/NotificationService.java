package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.NotificationResponse;

import java.util.Collection;
import java.util.List;

public interface NotificationService {

    List<NotificationResponse> getCurrentUserNotifications(Integer limit, Boolean unreadOnly);

    Long countCurrentUserUnread();

    void markAsRead(Long id);

    void markAllAsRead();

    void notifyUser(
            Long recipientUserId,
            Long actorUserId,
            String type,
            String title,
            String content,
            Long noteId,
            String targetUrl
    );

    void notifyUsers(
            Collection<Long> recipientUserIds,
            Long actorUserId,
            String type,
            String title,
            String content,
            Long noteId,
            String targetUrl
    );
}
