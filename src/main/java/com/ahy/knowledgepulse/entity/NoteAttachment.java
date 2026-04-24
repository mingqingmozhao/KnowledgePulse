package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_attachment")
public class NoteAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("original_name")
    private String originalName;

    @TableField("stored_name")
    private String storedName;

    @TableField("storage_path")
    private String storagePath;

    @TableField("file_url")
    private String fileUrl;

    @TableField("content_type")
    private String contentType;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
