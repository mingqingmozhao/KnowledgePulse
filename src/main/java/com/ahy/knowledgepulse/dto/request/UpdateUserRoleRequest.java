package com.ahy.knowledgepulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;
}
