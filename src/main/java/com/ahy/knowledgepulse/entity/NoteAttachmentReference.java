package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_attachment_reference")
public class NoteAttachmentReference {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("attachment_id")
    private Long attachmentId;

    @TableField("note_id")
    private Long noteId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
