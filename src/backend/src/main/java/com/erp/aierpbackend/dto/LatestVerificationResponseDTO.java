package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.VerificationRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@Slf4j
public class LatestVerificationResponseDTO {

    private String verificationRequestId;
    private String jobNo;
    private VerificationRequest.VerificationStatus status;
    private LocalDateTime requestTimestamp;
    private LocalDateTime resultTimestamp;
    private List<String> discrepancies;

    // Static factory method to convert from entity
    public static LatestVerificationResponseDTO fromEntity(VerificationRequest request, ObjectMapper objectMapper) {
        if (request == null) {
            return null;
        }

        List<String> discrepancyList = Collections.emptyList();
        if (request.getDiscrepanciesJson() != null && !request.getDiscrepanciesJson().isBlank()) {
            try {
                // Deserialize the JSON string back into a List<String>
                discrepancyList = objectMapper.readValue(request.getDiscrepanciesJson(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.error("Failed to deserialize discrepancies JSON for request ID {}: {}", request.getId(), e.getMessage());
                // Optionally include an error message in the list
                discrepancyList = List.of("Error reading discrepancies");
            }
        }

        return LatestVerificationResponseDTO.builder()
                .verificationRequestId(request.getId())
                .jobNo(request.getJobNo())
                .status(request.getStatus())
                .requestTimestamp(request.getRequestTimestamp())
                .resultTimestamp(request.getResultTimestamp())
                .discrepancies(discrepancyList)
                .build();
    }
}
