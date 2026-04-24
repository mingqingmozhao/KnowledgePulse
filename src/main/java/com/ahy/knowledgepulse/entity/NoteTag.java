package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("note_tag")
public class NoteTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("tag_name")
    private String tagName;
}
