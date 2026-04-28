package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {

    List<NoteTag> findByNoteId(Long noteId);

    List<String> findTagsByNoteId(Long noteId);

    List<NoteTag> findByTagName(@Param("userId") Long userId, @Param("tagName") String tagName);

    void deleteByNoteId(Long noteId);

    List<String> findAllTagsByUserId(Long userId);

    List<String> findTopTagsByUserId(Long userId);

    List<String> findTopAccessibleTagsByUserId(Long userId);
}
