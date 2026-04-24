package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

@Data
public class GraphNode {

    private Long id;

    private String name;

    private String type;

    public GraphNode(Long id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
}
