package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchResult {

    private Long id;

    private String title;

    private String snippet;

    private String tags;

    private LocalDateTime updateTime;
}
