package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields not explicitly defined
public class JobListDTO {

    @JsonProperty("no") // For serialization to frontend (lowercase)
    @JsonAlias("No")   // For deserialization from BC (uppercase)
    private String no; // Job Number

    // Using the exact OData field names provided
    @JsonProperty("_x0031_st_Check_Date")
    private String firstCheckDate; // Format likely "YYYY-MM-DD" or null

    @JsonProperty("_x0032_nd_Check_Date")
    private String secondCheckDate; // Format likely "YYYY-MM-DD" or null

    // Added field for 2nd Check By
    @JsonProperty("_x0032_nd_Check_By")
    private String secondCheckBy; // User ID or empty string

    @JsonProperty("Description")
    private String description; // Job Title

    @JsonProperty("Bill_to_Name")
    private String billToName; // Customer Name

    // Add other fields from Job_List if needed later
}
