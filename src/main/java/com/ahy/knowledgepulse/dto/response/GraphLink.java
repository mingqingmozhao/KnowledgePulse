package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

@Data
public class GraphLink {

    private Long source;

    private Long target;

    private String relationType;

    public GraphLink(Long source, Long target, String relationType) {
        this.source = source;
        this.target = target;
        this.relationType = relationType;
    }
}
