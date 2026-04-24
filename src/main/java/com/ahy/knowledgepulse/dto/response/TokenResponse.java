package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

@Data
public class TokenResponse {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private UserResponse user;

    public TokenResponse(String accessToken, String refreshToken, Long expiresIn, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}
