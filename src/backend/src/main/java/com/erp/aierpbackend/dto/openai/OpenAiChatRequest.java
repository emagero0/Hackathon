package com.erp.aierpbackend.dto.openai;

import lombok.Data;
import lombok.Builder; // Using builder pattern for easier construction
import java.util.List;

@Data
@Builder
public class OpenAiChatRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private Double temperature; // Optional: Defaults to 1 on OpenAI side
    private Integer max_tokens; // Optional
    // Add other parameters like top_p, frequency_penalty, etc. if needed
}
