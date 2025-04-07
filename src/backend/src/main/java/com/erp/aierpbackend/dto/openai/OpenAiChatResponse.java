package com.erp.aierpbackend.dto.openai;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Important for ignoring unknown fields

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields not defined in DTO
public class OpenAiChatResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<OpenAiChoice> choices;
    private OpenAiUsage usage;
    // Add system_fingerprint if needed
}
