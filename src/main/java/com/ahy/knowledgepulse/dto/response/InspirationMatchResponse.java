package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InspirationMatchResponse {

    private Long noteId;

    private String title;

    private List<String> matchedTags;

    private Integer score;

    private String reason;

    private LocalDateTime updateTime;
}
