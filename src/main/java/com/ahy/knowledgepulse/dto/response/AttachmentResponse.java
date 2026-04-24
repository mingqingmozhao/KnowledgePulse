package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentResponse {

    private Long id;

    private Long userId;

    private String originalName;

    private String fileName;

    private String fileType;

    private String contentType;

    private Long fileSize;

    private String fileUrl;

    private Long referenceCount;

    private Boolean used;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
