package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiFieldConfidence {
    private String fieldName; // Will contain prefixed name
    private String extractedValue;
    private double extractionConfidence;   // Confidence in the extracted value itself
    private Double matchAssessmentConfidence; // Optional: LLM's confidence in a fuzzy match
    private boolean verified;                 // Whether the field was considered "verified" by some criteria
    // verificationConfidence is retained if it serves a distinct purpose for backend-only verification steps,
    // otherwise, it might be redundant if matchAssessmentConfidence covers LLM's comparison attempts.
    // For now, assuming it might still be used by Java logic. If purely for LLM, it's covered by matchAssessmentConfidence.
    private double verificationConfidence; // Confidence in the match/mismatch decision (potentially backend determined)
}
