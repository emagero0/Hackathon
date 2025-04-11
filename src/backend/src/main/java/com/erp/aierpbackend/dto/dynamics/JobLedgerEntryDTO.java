package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate; // Assuming date format is compatible

@Data // Lombok annotation for getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields in JSON not defined in DTO
public class JobLedgerEntryDTO {

    @JsonProperty("Entry_No")
    private Integer entryNo;

    @JsonProperty("Job_No")
    private String jobNo;

    @JsonProperty("Posting_Date")
    private LocalDate postingDate; // Adjust type if necessary (e.g., ZonedDateTime, String)

    @JsonProperty("Document_No")
    private String documentNo;

    @JsonProperty("Type") // e.g., Resource, Item, G/L Account
    private String type;

    @JsonProperty("No") // e.g., Resource No., Item No., G/L Account No.
    private String no;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Quantity")
    private BigDecimal quantity;

    @JsonProperty("Unit_Cost_LCY") // Assuming LCY = Local Currency
    private BigDecimal unitCostLCY;

    @JsonProperty("Total_Cost_LCY")
    private BigDecimal totalCostLCY;

    @JsonProperty("Unit_Price_LCY")
    private BigDecimal unitPriceLCY;

    @JsonProperty("Total_Price_LCY")
    private BigDecimal totalPriceLCY;

    // Add other relevant fields as needed based on the OData response
    // Example:
    // @JsonProperty("Work_Type_Code")
    // private String workTypeCode;

    // @JsonProperty("Gen_Bus_Posting_Group")
    // private String genBusPostingGroup;

    // @JsonProperty("Gen_Prod_Posting_Group")
    // private String genProdPostingGroup;
}
