package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesInvoiceDTO {

    @JsonProperty("No")
    private String no; // Sales Invoice Number

    @JsonProperty("Sell_to_Customer_No")
    private String sellToCustomerNo;

    @JsonProperty("Sell_to_Customer_Name")
    private String sellToCustomerName;

    @JsonProperty("Amount")
    private java.math.BigDecimal amount; // Added field

    // Add other fields if needed
}
