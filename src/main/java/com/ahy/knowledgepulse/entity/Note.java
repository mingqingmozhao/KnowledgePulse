package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("folder_id")
    private Long folderId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("html_content")
    private String htmlContent;

    @TableField("tags")
    private String tags;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("share_token")
    private String shareToken;

    @TableField("share_password")
    private String sharePassword;

    @TableField("daily_note_date")
    private LocalDate dailyNoteDate;

    @TableField("is_deleted")
    private Integer deleted = 0;

    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
