package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiSafetyRating {
    private String category; // e.g., "HARM_CATEGORY_SEXUALLY_EXPLICIT"
    private String probability; // e.g., "NEGLIGIBLE", "LOW", "MEDIUM", "HIGH"
}
