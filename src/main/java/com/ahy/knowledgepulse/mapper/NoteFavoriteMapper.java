package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteFavorite;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteFavoriteMapper extends BaseMapper<NoteFavorite> {

    List<NoteFavorite> findByUserId(Long userId);

    NoteFavorite findByNoteIdAndUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);

    void deleteByNoteId(Long noteId);

    void deleteByNoteIdAndUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);
}
