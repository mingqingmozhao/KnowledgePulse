package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.ImportResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.NoteImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Markdown folder, Obsidian vault and batch note import")
public class ImportController {

    private final NoteImportService noteImportService;

    @PostMapping(value = "/markdown", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import Markdown files", description = "Import Markdown folders, Obsidian vaults and batch Markdown files")
    public Result<ImportResponse> importMarkdown(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "paths", required = false) List<String> paths,
            @RequestParam(value = "mode", defaultValue = "MARKDOWN_FOLDER") String mode,
            @RequestParam(value = "rootFolderName", required = false) String rootFolderName,
            @RequestParam(value = "targetFolderId", required = false) Long targetFolderId
    ) {
        return Result.success(noteImportService.importMarkdownFiles(files, paths, mode, rootFolderName, targetFolderId));
    }
}
