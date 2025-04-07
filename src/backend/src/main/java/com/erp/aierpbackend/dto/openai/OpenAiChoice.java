package com.erp.aierpbackend.dto.openai;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiChoice {
    private int index;
    private OpenAiMessage message; // Contains role and content
    private String finish_reason;
}
