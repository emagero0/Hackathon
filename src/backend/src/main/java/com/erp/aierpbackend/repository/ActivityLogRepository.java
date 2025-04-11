package com.erp.aierpbackend.repository;

import com.erp.aierpbackend.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Find logs related to a specific job, ordered by timestamp descending
    List<ActivityLog> findByRelatedJobIdOrderByTimestampDesc(Long relatedJobId);

    // Find all logs, ordered by timestamp descending (useful for general activity feed)
    // Use Pageable for efficient retrieval of recent logs
    Page<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

}
