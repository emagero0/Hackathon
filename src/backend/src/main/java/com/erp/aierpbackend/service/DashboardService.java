package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.DashboardStatsDTO;
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final JobRepository jobRepository;

    @Autowired
    public DashboardService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public DashboardStatsDTO calculateDashboardStats() {
        log.debug("Calculating dashboard statistics...");
        List<Job> allJobs = jobRepository.findAll();
        long totalJobs = allJobs.size();

        if (totalJobs == 0) {
            log.debug("No jobs found, returning zero stats.");
            return new DashboardStatsDTO(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0);
        }

        // Count jobs by status
        Map<Job.JobStatus, Long> countsByStatus = allJobs.stream()
                .collect(Collectors.groupingBy(Job::getStatus, Collectors.counting()));

        long verifiedJobs = countsByStatus.getOrDefault(Job.JobStatus.VERIFIED, 0L);
        long flaggedJobs = countsByStatus.getOrDefault(Job.JobStatus.FLAGGED, 0L);
        long pendingJobs = countsByStatus.getOrDefault(Job.JobStatus.PENDING, 0L) +
                           countsByStatus.getOrDefault(Job.JobStatus.PROCESSING, 0L); // Combine PENDING and PROCESSING
        long errorJobs = countsByStatus.getOrDefault(Job.JobStatus.ERROR, 0L);

        // Calculate percentages
        double verifiedPercentage = (double) verifiedJobs / totalJobs * 100.0;
        double flaggedPercentage = (double) flaggedJobs / totalJobs * 100.0;
        double pendingPercentage = (double) pendingJobs / totalJobs * 100.0;
        double errorPercentage = (double) errorJobs / totalJobs * 100.0;

        DashboardStatsDTO stats = new DashboardStatsDTO(
                totalJobs,
                verifiedJobs,
                flaggedJobs,
                pendingJobs,
                errorJobs,
                verifiedPercentage,
                flaggedPercentage,
                pendingPercentage,
                errorPercentage
        );

        log.debug("Calculated dashboard stats: {}", stats);
        return stats;
    }
}
