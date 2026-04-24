package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.GraphData;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteRelation;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteRelationMapper;
import com.ahy.knowledgepulse.service.impl.GraphServiceImpl;
import com.ahy.knowledgepulse.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceImplTest {

    @Mock
    private NoteRelationMapper relationMapper;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private NotePermissionService notePermissionService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private GraphServiceImpl graphService;

    @Test
    void getGlobalGraphShouldReturnAccessibleNodesAndLinks() {
        Note source = new Note();
        source.setId(1L);
        source.setTitle("Source");
        source.setDeleted(0);

        Note target = new Note();
        target.setId(2L);
        target.setTitle("Target");
        target.setDeleted(0);

        NoteRelation relation = new NoteRelation();
        relation.setId(1L);
        relation.setSourceNoteId(1L);
        relation.setTargetNoteId(2L);
        relation.setRelationType("引用");

        when(noteMapper.findAccessibleNotes(5L)).thenReturn(List.of(source, target));
        when(relationMapper.findRelationsByNoteIds(List.of(1L, 2L))).thenReturn(List.of(relation));

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            GraphData graphData = graphService.getGlobalGraph();

            assertThat(graphData.getNodes()).hasSize(2);
            assertThat(graphData.getLinks()).hasSize(1);
            assertThat(graphData.getLinks().get(0).getRelationType()).isEqualTo("引用");
        }
    }
}
