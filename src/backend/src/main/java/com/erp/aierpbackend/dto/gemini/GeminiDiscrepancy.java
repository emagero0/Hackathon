package com.erp.aierpbackend.dto.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiDiscrepancy {
    private String discrepancyType; // e.g., "VALUE_MISMATCH", "MISSING_IN_DOCUMENT"
    private String fieldName;       // e.g., "header.Sell_to_Customer_Name", "lines.0.Quantity"
    private String erpValue;        // Renamed from expectedValue
    private String documentValue;   // Renamed from actualValue
    private String description;     // Pre-formatted by LLM or set by Java
    private double confidence;      // LLM's confidence in this specific discrepancy (if LLM-generated) or backend confidence
    private String severity;        // Added field
}
