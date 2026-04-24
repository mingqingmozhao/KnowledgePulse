package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GraphData {

    private List<GraphNode> nodes = new ArrayList<>();

    private List<GraphLink> links = new ArrayList<>();
}
