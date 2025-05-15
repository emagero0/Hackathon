package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesQuoteLineDTO {

    @JsonProperty("Document_No")
    private String documentNo; // Sales Quote Number

    @JsonProperty("Line_No")
    private Integer lineNo;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Quantity")
    private BigDecimal quantity; // Use BigDecimal for precision

    // Add other fields if needed (e.g., Unit_Price)
}
