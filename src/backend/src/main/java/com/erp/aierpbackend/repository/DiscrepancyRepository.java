package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.Discrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {

    // Find all discrepancies associated with a specific VerificationResult ID
    List<Discrepancy> findByVerificationResultId(Long verificationResultId);

    // Add custom query methods if needed later.
}
