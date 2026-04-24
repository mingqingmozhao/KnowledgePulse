package com.ahy.knowledgepulse.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String nickname;

    private String avatar;

    private String email;

    private String currentPassword;

    private String newPassword;
}
