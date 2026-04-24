package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResponse {

    private Long rootFolderId;

    private String rootFolderName;

    private String mode;

    private Integer totalFiles = 0;

    private Integer importedNotes = 0;

    private Integer createdFolders = 0;

    private Integer skippedFiles = 0;

    private List<String> tags = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<ImportedNoteItem> notes = new ArrayList<>();

    @Data
    public static class ImportedNoteItem {

        private Long id;

        private String title;

        private String path;

        private Long folderId;

        private List<String> tags = new ArrayList<>();
    }
}
