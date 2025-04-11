package com.erp.aierpbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private long totalJobs;
    private long verifiedJobs;
    private long flaggedJobs;
    private long pendingJobs; // Includes PENDING and PROCESSING
    private long errorJobs;
    private double verifiedPercentage;
    private double flaggedPercentage;
    private double pendingPercentage; // Includes PENDING and PROCESSING
    private double errorPercentage;
}
