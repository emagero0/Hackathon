package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.dto.ActivityLogDTO;
import com.erp.aierpbackend.entity.ActivityLog;
import com.erp.aierpbackend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/activity-log") // Base path for activity log endpoints
public class ActivityLogController {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogController.class);

    private final ActivityLogService activityLogService;

    @Autowired
    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    /**
     * Get recent activity logs (paginated).
     * @param page Page number (default 0).
     * @param size Number of items per page (default 20).
     * @return Paginated list of ActivityLogDTOs.
     */
    @GetMapping
    public ResponseEntity<Page<ActivityLogDTO>> getRecentActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Request received for recent activity logs - page: {}, size: {}", page, size);
        Page<ActivityLog> activityPage = activityLogService.getRecentActivities(page, size);
        Page<ActivityLogDTO> dtoPage = activityPage.map(ActivityLogDTO::fromEntity);
        log.info("Returning {} activity logs on page {}", dtoPage.getNumberOfElements(), page);
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Get all activity logs for a specific job.
     * @param jobId The internal ID of the job.
     * @return List of ActivityLogDTOs for the job.
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<ActivityLogDTO>> getActivityForJob(@PathVariable Long jobId) {
        log.info("Request received for activity logs for Job ID: {}", jobId);
        List<ActivityLog> activityList = activityLogService.getActivityForJob(jobId);
        List<ActivityLogDTO> dtoList = activityList.stream()
                .map(ActivityLogDTO::fromEntity)
                .collect(Collectors.toList());
        log.info("Returning {} activity logs for Job ID: {}", dtoList.size(), jobId);
        return ResponseEntity.ok(dtoList);
    }
}
