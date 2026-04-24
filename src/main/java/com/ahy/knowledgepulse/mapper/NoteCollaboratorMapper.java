package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.dto.response.CollaboratorResponse;
import com.ahy.knowledgepulse.entity.NoteCollaborator;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteCollaboratorMapper extends BaseMapper<NoteCollaborator> {

    List<NoteCollaborator> findByNoteId(Long noteId);

    List<CollaboratorResponse> findResponsesByNoteId(Long noteId);

    List<NoteCollaborator> findByUserId(Long userId);

    NoteCollaborator findByNoteIdAndUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);

    void deleteByNoteId(Long noteId);
}
