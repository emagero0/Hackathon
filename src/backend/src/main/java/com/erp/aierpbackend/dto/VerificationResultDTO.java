package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.Job; // Import JobStatus
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VerificationResultDTO {

    private String jobNo;
    private Job.JobStatus status; // Overall status from Job entity
    private LocalDateTime verificationTimestamp;
    private List<String> discrepancies; // List of discrepancy descriptions

    // Static factory method or mapper could be used here for conversion
    // from Job/VerificationResult entities
}
