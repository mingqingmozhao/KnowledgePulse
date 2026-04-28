package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.Note;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    List<Note> findByUserId(Long userId);

    List<Note> findAccessibleNotes(Long userId);

    Note findAnyByUserIdAndDailyNoteDate(@Param("userId") Long userId, @Param("dailyNoteDate") LocalDate dailyNoteDate);

    List<Note> findDailyNotesByUserAndRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Note> findByFolderId(Long folderId);

    List<Note> findByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);

    List<Note> searchNotes(@Param("userId") Long userId, @Param("keyword") String keyword);

    List<Note> searchAccessibleNotes(@Param("userId") Long userId, @Param("keyword") String keyword);

    List<Note> findByTag(@Param("userId") Long userId, @Param("tagName") String tagName);

    List<Note> findPublicNotes();

    Note findByShareToken(String shareToken);

    List<Note> findRecentNotes(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    List<Note> findAccessibleRecentNotes(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    List<Note> findNotesWithTag(@Param("userId") Long userId, @Param("tagName") String tagName);

    List<Note> findAccessibleNotesWithTag(@Param("userId") Long userId, @Param("tagName") String tagName);

    List<Note> findAccessibleNotesByTags(@Param("userId") Long userId, @Param("tags") List<String> tags);

    LocalDateTime findLatestAccessibleUpdateTime(@Param("userId") Long userId);

    List<Note> findDeletedByUserId(Long userId);

    List<Note> findExpiredDeletedNotes(@Param("cutoff") LocalDateTime cutoff);

    int clearFolderForDeletedNotes(@Param("userId") Long userId, @Param("folderId") Long folderId);
}
