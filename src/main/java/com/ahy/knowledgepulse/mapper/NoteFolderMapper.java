package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.NoteFolder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteFolderMapper extends BaseMapper<NoteFolder> {

    List<NoteFolder> findByUserId(Long userId);

    List<NoteFolder> findByParentId(@Param("userId") Long userId, @Param("parentId") Long parentId);

    List<NoteFolder> findAllChildren(Long parentId);

    Integer countNotesInFolder(Long folderId);

    Integer countChildFolders(Long folderId);
}
