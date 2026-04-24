package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NoteVersionMapper extends BaseMapper<NoteVersion> {

    List<NoteVersion> findByNoteId(Long noteId);

    Integer getLatestVersion(Long noteId);

    void deleteByNoteId(Long noteId);
}
