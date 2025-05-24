package com.erp.aierpbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for the combined classification and verification result from the LLM service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyAndVerifyResultDTO {
    
    @JsonProperty("document_type")
    private String documentType;
    
    @JsonProperty("classification_confidence")
    private Double classificationConfidence;
    
    @JsonProperty("classification_reasoning")
    private String classificationReasoning;
    
    @JsonProperty("discrepancies")
    private List<DiscrepancyDTO> discrepancies = new ArrayList<>();
    
    @JsonProperty("field_confidences")
    private List<FieldConfidenceDTO> fieldConfidences = new ArrayList<>();
    
    @JsonProperty("overall_verification_confidence")
    private Double overallVerificationConfidence;
    
    @JsonProperty("raw_llm_response")
    private String rawLlmResponse;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    /**
     * DTO for a discrepancy found during verification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyDTO {
        
        @JsonProperty("field_name")
        private String fieldName;
        
        @JsonProperty("document_value")
        private String documentValue;
        
        @JsonProperty("erp_value")
        private String erpValue;
        
        @JsonProperty("severity")
        private String severity;
        
        @JsonProperty("description")
        private String description;

        @JsonProperty("discrepancy_type")
        private String discrepancyType;
    }
    
    /**
     * DTO for a field confidence score.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldConfidenceDTO {
        
        @JsonProperty("field_name")
        private String fieldName;
        
        @JsonProperty("extraction_confidence") // Renamed from confidence
        private Double extractionConfidence;
        
        @JsonProperty("extracted_value")
        private String extractedValue;
        
        @JsonProperty("verified")
        private Boolean verified;

        @JsonProperty("match_assessment_confidence")
        private Double matchAssessmentConfidence;
    }
}
