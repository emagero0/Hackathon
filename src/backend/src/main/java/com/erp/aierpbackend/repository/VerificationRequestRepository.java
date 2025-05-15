package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.VerificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, String> { // ID is String (UUID)

    // Find the latest verification request for a given job number
    Optional<VerificationRequest> findTopByJobNoOrderByRequestTimestampDesc(String jobNo);

    // Find all requests for a job number (potentially useful later)
    List<VerificationRequest> findByJobNoOrderByRequestTimestampDesc(String jobNo);

}
