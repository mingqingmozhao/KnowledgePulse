package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {

    private Long id;

    private Long recipientUserId;

    private Long actorUserId;

    private String actorUsername;

    private String actorNickname;

    private String actorAvatar;

    private String type;

    private String title;

    private String content;

    private Long noteId;

    private String noteTitle;

    private String targetUrl;

    private Boolean read;

    private LocalDateTime createTime;

    private LocalDateTime readTime;
}
