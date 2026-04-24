package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentResponse {

    private Long id;

    private Long noteId;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String content;

    private Boolean canDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
