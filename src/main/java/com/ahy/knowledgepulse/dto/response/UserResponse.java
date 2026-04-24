package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {

    private Long id;

    private String username;

    private String email;

    private String avatar;

    private String nickname;

    private String role;

    private LocalDateTime createTime;
}
