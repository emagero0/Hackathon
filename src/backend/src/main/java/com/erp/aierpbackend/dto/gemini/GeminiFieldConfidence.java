package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiFieldConfidence {
    private String fieldName;
    private String extractedValue;
    private double verificationConfidence; // Confidence in the match/mismatch decision
    private double extractionConfidence;   // Confidence in the extracted value itself
}
