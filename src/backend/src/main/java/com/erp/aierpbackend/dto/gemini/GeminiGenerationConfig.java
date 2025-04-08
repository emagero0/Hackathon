package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Only include non-null fields in JSON
public class GeminiGenerationConfig {
    private Double temperature;
    private Integer maxOutputTokens;
    private Double topP;
    private Integer topK;
    // Add other config fields like stopSequences if needed
}
