package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteRelationMapper extends BaseMapper<NoteRelation> {

    List<NoteRelation> findBySourceNoteId(Long sourceNoteId);

    List<NoteRelation> findByTargetNoteId(Long targetNoteId);

    List<NoteRelation> findByUserId(Long userId);

    void deleteByNoteId(Long noteId);

    List<NoteRelation> findRelationsByNoteIds(@Param("noteIds") List<Long> noteIds);
}
