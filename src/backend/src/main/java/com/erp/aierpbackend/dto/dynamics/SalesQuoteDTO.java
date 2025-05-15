package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesQuoteDTO {

    @JsonProperty("No")
    private String no; // Sales Quote Number

    @JsonProperty("Sell_to_Customer_No")
    private String sellToCustomerNo;

    @JsonProperty("Sell_to_Customer_Name")
    private String sellToCustomerName;

    @JsonProperty("Amount_Including_VAT")
    private java.math.BigDecimal amountIncludingVAT; // Added field

    // Add other fields if needed
}
