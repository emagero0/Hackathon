package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "verification_results")
@Getter
@Setter
@NoArgsConstructor
public class VerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true) // Foreign key to Job
    private Job job;

    @Column(name = "verification_timestamp", nullable = false)
    private LocalDateTime verificationTimestamp;

    @Column(name = "ai_confidence_score") // Optional: Store overall confidence
    private Double aiConfidenceScore;

    @Column(name = "raw_ai_response", columnDefinition = "TEXT") // Store raw response if needed
    private String rawAiResponse;

    @OneToMany(mappedBy = "verificationResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Discrepancy> discrepancies = new ArrayList<>();

    // Helper methods for managing discrepancies
    public void addDiscrepancy(Discrepancy discrepancy) {
        discrepancies.add(discrepancy);
        discrepancy.setVerificationResult(this);
    }

    public void removeDiscrepancy(Discrepancy discrepancy) {
        discrepancies.remove(discrepancy);
        discrepancy.setVerificationResult(null);
    }
}
