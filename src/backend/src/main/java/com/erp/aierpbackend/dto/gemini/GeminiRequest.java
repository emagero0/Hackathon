package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiRequest {
    private List<GeminiContent> contents;
    private GeminiGenerationConfig generationConfig; // Optional
    // Add safetySettings if needed
}
