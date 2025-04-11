package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.VerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationResultRepository extends JpaRepository<VerificationResult, Long> {
    // Basic CRUD methods are inherited from JpaRepository.
    // Add custom query methods if needed later.
}
