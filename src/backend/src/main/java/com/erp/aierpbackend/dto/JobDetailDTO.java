package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.Discrepancy;
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.VerificationResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private VerificationDetailsDTO verificationDetails;

    // Inner DTO for verification details
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationDetailsDTO {
        private LocalDateTime verificationTimestamp;
        private Double aiConfidenceScore;
        private String rawAiResponse;
        private List<DiscrepancyDTO> discrepancies;
    }

    // Inner DTO for discrepancy details
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyDTO {
        private String discrepancyType;
        private String fieldName;
        private String expectedValue;
        private String actualValue;
        private String description;

        public static DiscrepancyDTO fromEntity(Discrepancy discrepancy) {
            return new DiscrepancyDTO(
                    discrepancy.getDiscrepancyType(),
                    discrepancy.getFieldName(),
                    discrepancy.getExpectedValue(),
                    discrepancy.getActualValue(),
                    discrepancy.getDescription()
            );
        }
    }

    // Static factory method for easy conversion from Entity
    public static JobDetailDTO fromEntity(Job job) {
        VerificationDetailsDTO verificationDTO = null;
        if (job.getVerificationResult() != null) {
            VerificationResult vr = job.getVerificationResult();
            List<DiscrepancyDTO> discrepancyDTOs = vr.getDiscrepancies().stream()
                    .map(DiscrepancyDTO::fromEntity)
                    .collect(Collectors.toList());
            verificationDTO = new VerificationDetailsDTO(
                    vr.getVerificationTimestamp(),
                    vr.getAiConfidenceScore(),
                    vr.getRawAiResponse(),
                    discrepancyDTOs
            );
        }

        return new JobDetailDTO(
                job.getId(),
                job.getBusinessCentralJobId(),
                job.getJobTitle(),
                job.getCustomerName(),
                job.getStatus(),
                job.getLastProcessedAt(),
                verificationDTO
        );
    }
}
