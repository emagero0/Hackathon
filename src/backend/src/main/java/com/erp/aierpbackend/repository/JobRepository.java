package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime; // Import LocalDateTime
import java.util.List; // Import List
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    // Find a job by its unique Business Central ID
    Optional<Job> findByBusinessCentralJobId(String businessCentralJobId);

    // We can add more custom query methods here later if needed,
    // e.g., findByStatus, findByLastProcessedAtBefore, etc.

    // Method to find jobs processed on or after a certain timestamp
    List<Job> findByLastProcessedAtGreaterThanEqual(LocalDateTime dateTime);
}
