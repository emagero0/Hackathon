package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.Job;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
// Removed unused List/Collectors imports
// import java.util.List;
// import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailDTO {
    private Long internalId;
    private String businessCentralJobId;
    private String jobTitle;
    private String customerName;
    private Job.JobStatus status;
    private LocalDateTime lastProcessedAt;
    // Removed verificationDetails field

    // Removed inner VerificationDetailsDTO class

    // Removed inner DiscrepancyDTO class (no longer needed here)


    // Static factory method for easy conversion from Entity
    public static JobDetailDTO fromEntity(Job job) {
        // Removed logic related to VerificationResult and Discrepancy
        return new JobDetailDTO(
                job.getId(),
                job.getBusinessCentralJobId(),
                job.getJobTitle(),
                job.getCustomerName(),
                job.getStatus(),
                job.getLastProcessedAt()
                // Removed verificationDTO argument
        );
    }
}
