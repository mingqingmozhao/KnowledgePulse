package com.ahy.knowledgepulse.dto.request;

import lombok.Data;

@Data
public class ShareRequest {

    private Integer isPublic;

    private String password;
}
