package com.erp.aierpbackend.dto.dynamics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO for Job Attachment Links from Business Central.
 * Maps to the JobAttachmentLinks entity in Business Central.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAttachmentLinksDTO {

    @JsonProperty("No")
    private String no;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("File_Links")
    private String fileLinks;

    /**
     * Parses the File_Links field and returns an array of SharePoint URLs.
     * The File_Links field is a comma-separated list of URLs.
     * 
     * @return Array of SharePoint URLs
     */
    public String[] getSharePointUrls() {
        if (fileLinks == null || fileLinks.isEmpty()) {
            return new String[0];
        }
        return fileLinks.split(",");
    }
}
