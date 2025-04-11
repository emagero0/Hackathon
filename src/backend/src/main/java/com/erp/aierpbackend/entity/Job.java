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

    @OneToOne(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private VerificationResult verificationResult;

    // Helper method to link verification result
    public void setVerificationResult(VerificationResult verificationResult) {
        if (verificationResult == null) {
            if (this.verificationResult != null) {
                this.verificationResult.setJob(null);
            }
        } else {
            verificationResult.setJob(this);
        }
        this.verificationResult = verificationResult;
    }

    public enum JobStatus {
        PENDING,    // Initial state or awaiting processing
        PROCESSING, // Actively being checked by AI
        VERIFIED,   // AI check completed, no major issues found
        FLAGGED,    // AI check completed, issues found
        ERROR       // Error during processing
    }
}
