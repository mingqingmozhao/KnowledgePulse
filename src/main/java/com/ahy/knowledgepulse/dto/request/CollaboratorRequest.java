package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CollaboratorRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "权限不能为空")
    private String permission;
}
