package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "discrepancies")
@Getter
@Setter
@NoArgsConstructor
public class Discrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_result_id", nullable = false) // Foreign key to VerificationResult
    private VerificationResult verificationResult;

    @Column(name = "discrepancy_type", nullable = false)
    private String discrepancyType; // e.g., "AMOUNT_MISMATCH", "MISSING_SIGNATURE", "TAX_ERROR"

    @Column(name = "field_name") // Optional: Specific field where discrepancy occurred
    private String fieldName;

    @Column(name = "expected_value", columnDefinition = "TEXT") // Optional: Expected value
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT") // Optional: Actual value found
    private String actualValue;

    @Column(name = "description", columnDefinition = "TEXT") // Description of the discrepancy
    private String description;

}
