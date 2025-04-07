package com.erp.aierpbackend.dto.openai;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpenAiUsage {
    private int prompt_tokens;
    private int completion_tokens;
    private int total_tokens;
}
