package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.NoteTemplateRequest;
import com.ahy.knowledgepulse.dto.response.NoteTemplateResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.NoteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/template")
@RequiredArgsConstructor
@Tag(name = "Template", description = "Reusable note templates")
public class NoteTemplateController {

    private final NoteTemplateService templateService;

    @GetMapping("/list")
    @Operation(summary = "Get templates", description = "Fetch system templates and current user's custom templates")
    public Result<List<NoteTemplateResponse>> getTemplates() {
        return Result.success(templateService.getTemplates());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template detail", description = "Fetch a template by id")
    public Result<NoteTemplateResponse> getTemplate(@PathVariable Long id) {
        return Result.success(templateService.getTemplate(id));
    }

    @PostMapping
    @Operation(summary = "Create template", description = "Create a custom note template")
    public Result<NoteTemplateResponse> createTemplate(@Valid @RequestBody NoteTemplateRequest request) {
        return Result.success(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update template", description = "Update a custom note template")
    public Result<NoteTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody NoteTemplateRequest request
    ) {
        return Result.success(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete template", description = "Delete a custom note template")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success(null);
    }
}
