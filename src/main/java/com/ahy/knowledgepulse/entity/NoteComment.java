package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_comment")
public class NoteComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("user_id")
    private Long userId;

    @TableField("content")
    private String content;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
