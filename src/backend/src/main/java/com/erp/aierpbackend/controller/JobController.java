package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.config.RabbitMQConfig; // Import RabbitMQConfig
import com.erp.aierpbackend.dto.JobDetailDTO;
import com.erp.aierpbackend.dto.JobSummaryDTO;
// Removed unused imports related to old verification endpoints
// import com.erp.aierpbackend.dto.TriggerVerificationRequest;
// import com.erp.aierpbackend.dto.VerificationResultDTO;
// import com.erp.aierpbackend.entity.Discrepancy;
import com.erp.aierpbackend.entity.Job;
// import com.erp.aierpbackend.entity.VerificationResult;
import com.erp.aierpbackend.repository.JobRepository;
// import jakarta.validation.Valid;
// import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime; // Import LocalDateTime
import java.util.ArrayList; // Import ArrayList
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs") // Base path for job-related endpoints
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobRepository jobRepository;
    // Removed RabbitTemplate injection as it's no longer used here

    @Autowired
    public JobController(JobRepository jobRepository) { // Removed RabbitTemplate from constructor
        this.jobRepository = jobRepository;
    }

    /**
     * Get a list of all jobs with summary information.
     * TODO: Add pagination and filtering capabilities.
     * @return List of JobSummaryDTOs.
     */
    @GetMapping
    public ResponseEntity<List<JobSummaryDTO>> getAllJobs() {
        log.info("Request received for getting all job summaries");
        List<Job> jobs = jobRepository.findAll(); // Consider Pageable for large datasets
        List<JobSummaryDTO> jobSummaries = jobs.stream()
                .map(JobSummaryDTO::fromEntity)
                .collect(Collectors.toList());
        log.info("Returning {} job summaries", jobSummaries.size());
        return ResponseEntity.ok(jobSummaries);
    }

    /**
     * Get detailed information for a single job by its internal ID.
     * @param id The internal database ID of the job.
     * @return JobDetailDTO or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobDetailDTO> getJobById(@PathVariable Long id) {
        log.info("Request received for job details with internal ID: {}", id);
        return jobRepository.findById(id) // Fetch job by internal ID
                .map(job -> {
                    log.info("Found job with internal ID: {}", id);
                    // Eagerly fetch associations if needed, though DTO mapping handles it here
                    // Hibernate.initialize(job.getVerificationResult()); // Example if lazy loading issues occur
                    // if (job.getVerificationResult() != null) {
                    //     Hibernate.initialize(job.getVerificationResult().getDiscrepancies());
                    // }
                    return ResponseEntity.ok(JobDetailDTO.fromEntity(job));
                })
                .orElseGet(() -> {
                    log.warn("Job not found with internal ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    // Removed old /trigger-verification endpoint (moved to VerificationController)

    // Removed old /{jobNo}/verification-result endpoint (moved to VerificationController)
}
