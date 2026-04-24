package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderRequest {

    @NotBlank(message = "文件夹名称不能为空")
    private String name;

    private Long parentId;
}
