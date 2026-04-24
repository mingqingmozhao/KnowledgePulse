package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.NotificationResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteNotification;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteNotificationMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.NotificationService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final NoteNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final NoteMapper noteMapper;

    @Override
    public List<NotificationResponse> getCurrentUserNotifications(Integer limit, Boolean unreadOnly) {
        Long currentUserId = requireCurrentUser();
        LambdaQueryWrapper<NoteNotification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteNotification::getRecipientUserId, currentUserId);

        if (Boolean.TRUE.equals(unreadOnly)) {
            queryWrapper.eq(NoteNotification::getReadFlag, 0);
        }

        queryWrapper.orderByAsc(NoteNotification::getReadFlag)
                .orderByDesc(NoteNotification::getCreateTime)
                .last("LIMIT " + normalizeLimit(limit));

        return notificationMapper.selectList(queryWrapper)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    public Long countCurrentUserUnread() {
        Long currentUserId = requireCurrentUser();
        LambdaQueryWrapper<NoteNotification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteNotification::getRecipientUserId, currentUserId)
                .eq(NoteNotification::getReadFlag, 0);
        return notificationMapper.selectCount(queryWrapper);
    }

    @Override
    public void markAsRead(Long id) {
        Long currentUserId = requireCurrentUser();
        NoteNotification notification = notificationMapper.selectById(id);

        if (notification == null || !currentUserId.equals(notification.getRecipientUserId())) {
            throw new BusinessException(404, "Notification does not exist");
        }

        if (Integer.valueOf(1).equals(notification.getReadFlag())) {
            return;
        }

        notification.setReadFlag(1);
        notification.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    @Override
    public void markAllAsRead() {
        Long currentUserId = requireCurrentUser();
        LambdaUpdateWrapper<NoteNotification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(NoteNotification::getRecipientUserId, currentUserId)
                .eq(NoteNotification::getReadFlag, 0)
                .set(NoteNotification::getReadFlag, 1)
                .set(NoteNotification::getReadTime, LocalDateTime.now());
        notificationMapper.update(null, updateWrapper);
    }

    @Override
    public void notifyUser(
            Long recipientUserId,
            Long actorUserId,
            String type,
            String title,
            String content,
            Long noteId,
            String targetUrl
    ) {
        if (recipientUserId == null || !StringUtils.hasText(type) || !StringUtils.hasText(title)) {
            return;
        }

        if (actorUserId != null && recipientUserId.equals(actorUserId)) {
            return;
        }

        try {
            NoteNotification notification = new NoteNotification();
            notification.setRecipientUserId(recipientUserId);
            notification.setActorUserId(actorUserId);
            notification.setType(trim(type, 40).toUpperCase());
            notification.setTitle(trim(title, 120));
            notification.setContent(trim(content, 500));
            notification.setNoteId(noteId);
            notification.setTargetUrl(trim(targetUrl, 255));
            notification.setReadFlag(0);
            notificationMapper.insert(notification);
        } catch (Exception ex) {
            log.warn("Failed to persist notification for user {} type {}: {}", recipientUserId, type, ex.getMessage());
        }
    }

    @Override
    public void notifyUsers(
            Collection<Long> recipientUserIds,
            Long actorUserId,
            String type,
            String title,
            String content,
            Long noteId,
            String targetUrl
    ) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        Set<Long> uniqueRecipients = new LinkedHashSet<>(recipientUserIds);
        uniqueRecipients.forEach(recipientUserId ->
                notifyUser(recipientUserId, actorUserId, type, title, content, noteId, targetUrl)
        );
    }

    private Long requireCurrentUser() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return currentUserId;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private NotificationResponse convertToResponse(NoteNotification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setRecipientUserId(notification.getRecipientUserId());
        response.setActorUserId(notification.getActorUserId());
        response.setType(notification.getType());
        response.setTitle(notification.getTitle());
        response.setContent(notification.getContent());
        response.setNoteId(notification.getNoteId());
        response.setTargetUrl(notification.getTargetUrl());
        response.setRead(Integer.valueOf(1).equals(notification.getReadFlag()));
        response.setCreateTime(notification.getCreateTime());
        response.setReadTime(notification.getReadTime());

        if (notification.getActorUserId() != null) {
            User actor = userMapper.selectById(notification.getActorUserId());
            if (actor != null) {
                response.setActorUsername(actor.getUsername());
                response.setActorNickname(actor.getNickname());
                response.setActorAvatar(actor.getAvatar());
            }
        }

        if (notification.getNoteId() != null) {
            Note note = noteMapper.selectById(notification.getNoteId());
            if (note != null) {
                response.setNoteTitle(note.getTitle());
            }
        }

        return response;
    }
}
