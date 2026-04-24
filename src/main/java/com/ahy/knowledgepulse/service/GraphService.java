package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.RelationRequest;
import com.ahy.knowledgepulse.dto.response.GraphData;

public interface GraphService {

    GraphData getGraphData(Long noteId);

    GraphData getGlobalGraph();

    void addRelation(RelationRequest request);

    void deleteRelation(Long id);
}
