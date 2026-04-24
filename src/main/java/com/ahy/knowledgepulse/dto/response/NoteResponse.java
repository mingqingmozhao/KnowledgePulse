package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NoteResponse {

    private Long id;

    private String title;

    private String content;

    private String htmlContent;

    private List<String> tags;

    private Long folderId;

    private String folderName;

    private Long ownerUserId;

    private String ownerUsername;

    private String ownerNickname;

    private String ownerAvatar;

    private String currentUserPermission;

    private Boolean currentUserCanManage;

    private Integer isPublic;

    private LocalDate dailyNoteDate;

    private Boolean dailyNote;

    private Boolean favorited;

    private LocalDateTime favoriteTime;

    private Integer deleted;

    private LocalDateTime deletedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
