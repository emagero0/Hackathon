package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.dto.DashboardStatsDTO;
import com.erp.aierpbackend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import com.erp.aierpbackend.dto.DailyVerificationStatsDTO; // Import new DTO
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List; // Import List

@RestController
@RequestMapping("/api/dashboard") // Base path for dashboard-related endpoints
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get dashboard statistics (job counts and percentages by status).
     * @return DashboardStatsDTO.
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        log.info("Request received for dashboard statistics");
        DashboardStatsDTO stats = dashboardService.calculateDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get daily verification statistics for the past week for the chart.
     * @return List of DailyVerificationStatsDTO.
     */
    @GetMapping("/daily-stats")
    public ResponseEntity<List<DailyVerificationStatsDTO>> getDailyStats() {
        log.info("Request received for daily verification statistics");
        List<DailyVerificationStatsDTO> dailyStats = dashboardService.getProcessedDailyVerificationStats();
        return ResponseEntity.ok(dailyStats);
    }

    // Add other dashboard-related endpoints here later if needed
    // e.g., endpoints for recent activity, daily breakdown charts, etc.
}
