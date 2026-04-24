package com.ahy.knowledgepulse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportPayload {

    private String fileName;

    private String contentType;

    private byte[] content;
}
