package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FolderResponse {

    private Long id;

    private String name;

    private Long parentId;

    private List<FolderResponse> children;

    private List<NoteResponse> notes;

    private LocalDateTime createTime;
}
