package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NoteRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    private String htmlContent;

    private List<String> tags;

    private Long folderId;

    private List<Long> attachmentIds;
}
