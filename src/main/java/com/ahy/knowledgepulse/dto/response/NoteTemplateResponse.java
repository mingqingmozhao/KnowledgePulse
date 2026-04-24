package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoteTemplateResponse {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String content;

    private String htmlContent;

    private List<String> tags;

    private String category;

    private Boolean system;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
