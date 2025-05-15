package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.DashboardStatsDTO;
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate; // Import LocalDate
import java.time.LocalDateTime; // Import LocalDateTime
import com.erp.aierpbackend.dto.DailyVerificationStatsDTO; // Import DTO

import java.time.DayOfWeek; // Import DayOfWeek
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle; // Import TextStyle
import java.util.ArrayList; // Import ArrayList
import java.util.List;
import java.util.Locale; // Import Locale
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

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

    // Renamed original method to indicate it fetches raw data
    private Map<LocalDate, Map<Job.JobStatus, Long>> fetchAndGroupDailyStats() {
        log.debug("Fetching and grouping daily verification stats for the last week...");
        // Go back 6 days to get today + previous 6 days = 7 days total
        LocalDateTime startDate = LocalDate.now().minusDays(6).atStartOfDay();

        // Fetch jobs processed since the start date
        List<Job> recentJobs = jobRepository.findByLastProcessedAtGreaterThanEqual(startDate);

        // Group by date (LocalDate) and then by status
        Map<LocalDate, Map<Job.JobStatus, Long>> dailyStats = recentJobs.stream()
                .filter(job -> job.getLastProcessedAt() != null) // Ensure timestamp exists
                .collect(Collectors.groupingBy(
                        job -> job.getLastProcessedAt().toLocalDate(), // Group by date part
                        LinkedHashMap::new, // Use LinkedHashMap to maintain date order
                        Collectors.groupingBy(Job::getStatus, Collectors.counting())
                ));

        log.debug("Raw grouped daily stats: {}", dailyStats);
        return dailyStats;
    }

    // New public method to process raw data into DTO list
    public List<DailyVerificationStatsDTO> getProcessedDailyVerificationStats() {
        Map<LocalDate, Map<Job.JobStatus, Long>> rawStats = fetchAndGroupDailyStats();
        List<DailyVerificationStatsDTO> processedStats = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Iterate through the last 7 days (today back to 6 days ago)
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<Job.JobStatus, Long> countsForDay = rawStats.getOrDefault(date, Map.of()); // Get counts or empty map

            long verified = countsForDay.getOrDefault(Job.JobStatus.VERIFIED, 0L);
            long flagged = countsForDay.getOrDefault(Job.JobStatus.FLAGGED, 0L);
            // Combine pending, processing, and error into one category for the chart
            long pendingOrError = countsForDay.getOrDefault(Job.JobStatus.PENDING, 0L) +
                                  countsForDay.getOrDefault(Job.JobStatus.PROCESSING, 0L) +
                                  countsForDay.getOrDefault(Job.JobStatus.ERROR, 0L);

            // Format date (e.g., "Mon", "Tue")
            String dayLabel = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            processedStats.add(new DailyVerificationStatsDTO(dayLabel, verified, flagged, pendingOrError));
        }

        log.debug("Processed daily stats for chart: {}", processedStats);
        return processedStats;
    }
}
