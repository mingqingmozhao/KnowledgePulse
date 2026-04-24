package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_version")
public class NoteVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("version")
    private Integer version;

    @TableField("content_snapshot")
    private String contentSnapshot;

    @TableField("create_time")
    private LocalDateTime createTime;
}
