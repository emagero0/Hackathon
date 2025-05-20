package com.erp.aierpbackend.dto.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for document classification results from Gemini.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentClassificationResult {

    @JsonProperty("document_type")
    private String documentType;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("reasoning")
    private String reasoning;
}
