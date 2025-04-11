package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.Job;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSummaryDTO {
    private Long internalId; // Our database ID
    private String businessCentralJobId;
    private String jobTitle;
    private String customerName;
    private Job.JobStatus status;
    private LocalDateTime lastProcessedAt;

    // Static factory method for easy conversion from Entity
    public static JobSummaryDTO fromEntity(Job job) {
        return new JobSummaryDTO(
                job.getId(),
                job.getBusinessCentralJobId(),
                job.getJobTitle(),
                job.getCustomerName(),
                job.getStatus(),
                job.getLastProcessedAt()
        );
    }
}
