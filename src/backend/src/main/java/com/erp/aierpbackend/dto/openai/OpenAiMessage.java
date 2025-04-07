package com.erp.aierpbackend.dto.openai;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiMessage {
    private String role; // e.g., "system", "user", "assistant"
    private String content;
}
