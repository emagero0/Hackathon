package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {

    // Find all feedback entries for a specific job
    List<UserFeedback> findByJobId(Long jobId);

    // Add other query methods if needed, e.g., find by userIdentifier, find by timestamp range
}
