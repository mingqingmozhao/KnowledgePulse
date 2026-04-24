package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationLogResponse {

    private Long id;

    private Long userId;

    private String module;

    private String operation;

    private LocalDateTime createTime;
}
