package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.dto.LatestVerificationResponseDTO;
import com.erp.aierpbackend.dto.TriggerVerificationRequest;
import com.erp.aierpbackend.dto.dynamics.JobListDTO; // Added
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.VerificationRequest;
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.VerificationRequestRepository;
import com.erp.aierpbackend.service.BusinessCentralService; // Added
import com.erp.aierpbackend.service.VerificationProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.Builder; // Added
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // Added

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects; // Import Objects for null check helper

@RestController
@RequestMapping("/api/verifications")
@Slf4j
public class VerificationController {

    private final VerificationRequestRepository verificationRequestRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final VerificationProcessingService verificationProcessingService;
    private final BusinessCentralService businessCentralService; // Added field

    // Inner class for the response of the trigger endpoint
    @Data
    private static class TriggerResponse {
        private String verificationRequestId;
    }

    @Autowired
    public VerificationController(VerificationRequestRepository verificationRequestRepository,
                                  JobRepository jobRepository,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate,
                                  VerificationProcessingService verificationProcessingService,
                                  BusinessCentralService businessCentralService) { // Added BC Service injection
        this.verificationRequestRepository = verificationRequestRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.verificationProcessingService = verificationProcessingService;
        this.businessCentralService = businessCentralService; // Added assignment
    }

    /**
     * Initiates a new verification request for a given job number.
     * Creates a VerificationRequest record and triggers async processing.
     * @param request DTO containing the job number.
     * @return HTTP 202 Accepted with the new verificationRequestId.
     */
    @PostMapping
    public ResponseEntity<TriggerResponse> requestVerification(@Valid @RequestBody TriggerVerificationRequest request) {
        final String jobNo = request.getJobNo();
        log.info("Request received to initiate verification for Job No: {}", jobNo);

        VerificationRequest savedRequest = transactionTemplate.execute(status -> {
            Job job = jobRepository.findByBusinessCentralJobId(jobNo)
                    .orElseGet(() -> {
                        log.info("Creating new Job entity for verification request, Job No: {}", jobNo);
                        Job newJob = new Job();
                        newJob.setBusinessCentralJobId(jobNo);
                        newJob.setStatus(Job.JobStatus.PENDING);
                        return jobRepository.save(newJob);
                    });

            VerificationRequest verificationRequest = new VerificationRequest(jobNo);
            VerificationRequest savedVerificationRequest = verificationRequestRepository.save(verificationRequest);
            log.info("Created VerificationRequest with ID: {} for Job No: {}", savedVerificationRequest.getId(), jobNo);

            final String requestId = savedVerificationRequest.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transaction committed for Request ID: {}. Triggering async processing.", requestId);
                    try {
                        verificationProcessingService.processVerification(requestId, jobNo);
                        log.info("Successfully triggered async verification processing for Request ID: {}", requestId);
                    } catch (Exception e) {
                        log.error("Failed to trigger async processing AFTER commit for Request ID: {}", requestId, e);
                    }
                }
            });
            return savedVerificationRequest;
        });

        if (savedRequest == null) {
            log.error("Transaction failed for verification request, Job No: {}", jobNo);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        TriggerResponse responseBody = new TriggerResponse();
        responseBody.setVerificationRequestId(savedRequest.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);
    }

    // --- New Eligibility Check Endpoint ---

    @Data
    @Builder // Added Builder annotation
    public static class EligibilityCheckResponseDTO {
        private boolean isEligible;
        private String jobNo;
        private String jobTitle;
        private String customerName;
        private String message;
    }

    @GetMapping("/check-eligibility/{jobNo}")
    public Mono<ResponseEntity<EligibilityCheckResponseDTO>> checkEligibility(@PathVariable String jobNo) {
        log.info("Request received to check eligibility for Job No: {}", jobNo);
        return businessCentralService.fetchJobListEntry(jobNo) // Use injected service
            .map(jobListEntry -> {
                // Updated Eligibility Logic: Check if first check is done and second check person is empty
                boolean isFirstCheckDone = jobListEntry.getFirstCheckDate() != null && !jobListEntry.getFirstCheckDate().isBlank();
                boolean isSecondCheckPersonEmpty = jobListEntry.getSecondCheckBy() == null || jobListEntry.getSecondCheckBy().isBlank();
                boolean isEligible = isFirstCheckDone && isSecondCheckPersonEmpty;

                // Log detailed eligibility check information
                log.info("Eligibility check details for Job No: {}", jobNo);
                log.info("First Check Date: '{}', Second Check By: '{}'",
                         jobListEntry.getFirstCheckDate(), jobListEntry.getSecondCheckBy());
                log.info("isFirstCheckDone: {}, isSecondCheckPersonEmpty: {}, isEligible: {}",
                         isFirstCheckDone, isSecondCheckPersonEmpty, isEligible);

                String message;

                if (isEligible) {
                    message = "Eligible for second check.";
                } else if (!isFirstCheckDone) {
                    message = "First check has not been completed.";
                } else { // Implies second check person is already assigned
                    message = "Second check has already been assigned to: " + jobListEntry.getSecondCheckBy();
                }

                EligibilityCheckResponseDTO response = EligibilityCheckResponseDTO.builder()
                        .isEligible(isEligible)
                        .jobNo(jobListEntry.getNo())
                        .jobTitle(jobListEntry.getDescription())
                        .customerName(jobListEntry.getBillToName())
                        .message(message)
                        .build();
                return ResponseEntity.ok(response);
            })
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    EligibilityCheckResponseDTO.builder() // Use builder
                            .isEligible(false)
                            .jobNo(jobNo)
                            .message("Job not found in Business Central.")
                            .build()
            ))
            .onErrorResume(e -> {
                log.error("Error fetching job list entry for eligibility check, Job No: {}", jobNo, e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        EligibilityCheckResponseDTO.builder() // Use builder
                                .isEligible(false)
                                .jobNo(jobNo)
                                .message("Error checking eligibility: " + e.getMessage())
                                .build()
                ));
            });
    }

    // --- End of New Endpoint ---

    /**
     * Gets the details of a specific verification request by its ID.
     * @param id The UUID of the VerificationRequest.
     * @return VerificationRequest details or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<VerificationRequest> getVerificationRequestById(@PathVariable String id) {
        log.info("Request received for VerificationRequest details with ID: {}", id);
        return verificationRequestRepository.findById(id)
                .map(request -> {
                    log.info("Found VerificationRequest with ID: {}", id);
                    return ResponseEntity.ok(request);
                })
                .orElseGet(() -> {
                    log.warn("VerificationRequest not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Gets the latest verification request details for a given Job Number.
     * @param jobNo The Business Central Job ID.
     * @return LatestVerificationResponseDTO or 404 Not Found.
     */
    @GetMapping("/job/{jobNo}/latest")
    public ResponseEntity<LatestVerificationResponseDTO> getLatestVerificationForJob(@PathVariable String jobNo) {
        log.info("Request received for latest verification result for Job No: {}", jobNo);
        return verificationRequestRepository.findTopByJobNoOrderByRequestTimestampDesc(jobNo)
                .map(request -> {
                    log.info("Found latest VerificationRequest (ID: {}) for Job No: {}", request.getId(), jobNo);
                    LatestVerificationResponseDTO dto = LatestVerificationResponseDTO.fromEntity(request, objectMapper);
                    return ResponseEntity.ok(dto);
                })
                .orElseGet(() -> {
                    log.warn("No verification request found for Job No: {}", jobNo);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Gets a list of jobs pending second check from Business Central.
     * @return List of JobListDTO objects representing jobs pending second check
     */
    @GetMapping("/jobs-pending-second-check")
    public Mono<ResponseEntity<List<JobListDTO>>> getJobsPendingSecondCheck() {
        log.info("Request received for jobs pending second check");
        return businessCentralService.fetchJobsPendingSecondCheck()
                .collectList()
                .map(jobs -> {
                    log.info("Found {} jobs pending second check", jobs.size());
                    // Log each job for debugging
                    jobs.forEach(job -> {
                        log.info("Returning job to frontend: No={}, FirstCheckDate={}, SecondCheckBy={}, Description={}",
                                job.getNo(), job.getFirstCheckDate(), job.getSecondCheckBy(), job.getDescription());
                    });
                    return ResponseEntity.ok(jobs);
                })
                .onErrorResume(e -> {
                    log.error("Error fetching jobs pending second check", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
