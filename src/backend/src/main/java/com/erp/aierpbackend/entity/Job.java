package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_central_job_id", nullable = false, unique = true)
    private String businessCentralJobId; // Assuming a unique ID from BC

    @Column(name = "job_title")
    private String jobTitle; // Optional: Store title if available

    @Column(name = "customer_name")
    private String customerName; // Optional: Store customer if available

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "last_processed_at")
    private LocalDateTime lastProcessedAt;

    @Column(name = "verification_result", columnDefinition = "TEXT")
    private String verificationResult;

    @Column(name = "has_discrepancies")
    private Boolean hasDiscrepancies = false;

    public enum JobStatus {
        PENDING,    // Initial state or awaiting processing
        PROCESSING, // Actively being checked by AI
        VERIFIED,   // AI check completed, no major issues found
        FLAGGED,    // AI check completed, issues found
        SKIPPED,    // Verification skipped (e.g., did not qualify)
        ERROR       // Error during processing
    }
}
