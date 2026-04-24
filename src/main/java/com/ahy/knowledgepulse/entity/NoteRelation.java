package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_relation")
public class NoteRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_note_id")
    private Long sourceNoteId;

    @TableField("target_note_id")
    private Long targetNoteId;

    @TableField("relation_type")
    private String relationType;

    @TableField("create_time")
    private LocalDateTime createTime;
}
