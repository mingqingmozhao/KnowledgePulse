package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.ImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NoteImportService {

    ImportResponse importMarkdownFiles(
            List<MultipartFile> files,
            List<String> paths,
            String mode,
            String rootFolderName,
            Long targetFolderId
    );
}
