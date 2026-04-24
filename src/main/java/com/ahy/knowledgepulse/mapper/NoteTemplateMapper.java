package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteTemplateMapper extends BaseMapper<NoteTemplate> {

    List<NoteTemplate> findAccessibleByUserId(@Param("userId") Long userId);

    NoteTemplate findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    NoteTemplate findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
}
