package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VersionResponse {

    private Long id;

    private Integer version;

    private String contentSnapshot;

    private LocalDateTime createTime;
}
