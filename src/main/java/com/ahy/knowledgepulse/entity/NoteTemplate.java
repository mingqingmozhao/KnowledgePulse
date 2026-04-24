package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_template")
public class NoteTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("content")
    private String content;

    @TableField("html_content")
    private String htmlContent;

    @TableField("tags")
    private String tags;

    @TableField("category")
    private String category;

    @TableField("is_system")
    private Integer systemFlag;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
