package com.ahy.knowledgepulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_notification")
public class NoteNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("recipient_user_id")
    private Long recipientUserId;

    @TableField("actor_user_id")
    private Long actorUserId;

    @TableField("type")
    private String type;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("note_id")
    private Long noteId;

    @TableField("target_url")
    private String targetUrl;

    @TableField("read_flag")
    private Integer readFlag = 0;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("read_time")
    private LocalDateTime readTime;
}
