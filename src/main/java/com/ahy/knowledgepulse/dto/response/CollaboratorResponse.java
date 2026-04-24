package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollaboratorResponse {

    private Long id;

    private Long noteId;

    private Long userId;

    private String permission;

    private LocalDateTime createTime;

    private String username;

    private String email;

    private String avatar;

    private String nickname;
}
