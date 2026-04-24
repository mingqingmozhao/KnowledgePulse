package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NoteTemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    private String content;

    private String htmlContent;

    private List<String> tags;

    private String category;
}
