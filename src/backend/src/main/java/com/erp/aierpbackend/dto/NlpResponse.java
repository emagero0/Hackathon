package com.erp.aierpbackend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NlpResponse {
    private String status; // e.g., "VERIFIED", "FLAGGED"
    private List<Discrepancy> discrepancies;
    private String explanation; // Natural language explanation
    private Map<String, Object> processingMetadata; // e.g., confidence, model version

    // Inner class for discrepancy details
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Discrepancy {
        private String field; // e.g., "invoice_amount"
        private String expectedValue;
        private String actualValue;
        private String description;
    }
}
