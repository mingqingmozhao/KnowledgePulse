package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.AttachmentResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.NoteAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/attachment")
@RequiredArgsConstructor
@Tag(name = "Attachment", description = "Upload, reference and manage note attachments")
public class AttachmentController {

    private final NoteAttachmentService attachmentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload attachment", description = "Upload image, PDF, Word .doc or .docx attachment")
    public Result<AttachmentResponse> upload(@RequestPart("file") MultipartFile file) {
        return Result.success(attachmentService.upload(file));
    }

    @GetMapping
    @Operation(summary = "List attachments", description = "List current user's attachments, optionally unused only")
    public Result<List<AttachmentResponse>> list(
            @RequestParam(required = false) String fileType,
            @RequestParam(defaultValue = "false") Boolean unusedOnly,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(attachmentService.list(fileType, unusedOnly, keyword));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete unused attachment", description = "Delete an attachment only when it is not referenced by notes")
    public Result<Void> delete(@org.springframework.web.bind.annotation.PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return Result.success(null);
    }
}
