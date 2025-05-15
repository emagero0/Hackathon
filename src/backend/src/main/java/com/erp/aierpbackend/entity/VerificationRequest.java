package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_requests", indexes = {
    @Index(name = "idx_verification_request_job_no", columnList = "jobNo"),
    @Index(name = "idx_verification_request_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Generate UUID automatically
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "VARCHAR(36)") // Use VARCHAR for UUID
    @JdbcTypeCode(SqlTypes.VARCHAR) // Ensure correct JDBC type mapping
    private String id;

    @Column(nullable = false)
    private String jobNo; // The Business Central Job ID

    @CreationTimestamp // Automatically set on creation
    @Column(nullable = false, updatable = false)
    private LocalDateTime requestTimestamp;

    @Column
    private LocalDateTime resultTimestamp; // When processing finished

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25) // Added length = 25
    private VerificationStatus status = VerificationStatus.PENDING;

    @Lob // Use Lob for potentially large JSON string
    @Column(columnDefinition = "TEXT") // Ensure TEXT type in DB
    private String discrepanciesJson; // Store List<String> as JSON

    public enum VerificationStatus {
        PENDING,    // Request received, awaiting processing
        PROCESSING, // Verification in progress
        COMPLETED,  // Process finished (may have discrepancies)
        SKIPPED,    // Process skipped (e.g., job did not qualify)
        FAILED      // Process failed due to error
    }

    // Constructor for easier creation if needed (e.g., in controller)
    public VerificationRequest(String jobNo) {
        this.jobNo = jobNo;
        this.status = VerificationStatus.PENDING;
        // ID and requestTimestamp are generated automatically
    }
}
