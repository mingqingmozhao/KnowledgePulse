package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RelationRequest {

    @NotNull(message = "源笔记ID不能为空")
    private Long sourceNoteId;

    @NotNull(message = "目标笔记ID不能为空")
    private Long targetNoteId;

    @NotBlank(message = "关系类型不能为空")
    private String relationType;
}
