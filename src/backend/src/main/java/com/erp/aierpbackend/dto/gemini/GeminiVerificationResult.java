package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiVerificationResult {
    private List<GeminiDiscrepancy> discrepancies;
    private List<GeminiFieldConfidence> fieldConfidences;
    private Map<String, Object> extractedValues; // Flexible structure for extracted data
    private double overallVerificationConfidence;
}
