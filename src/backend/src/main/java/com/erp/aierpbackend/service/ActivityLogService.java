package com.erp.aierpbackend.service;

import com.erp.aierpbackend.entity.ActivityLog;
import com.erp.aierpbackend.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository activityLogRepository;

    // Define standard event types
    public static final String EVENT_JOB_PROCESSED = "JOB_PROCESSED";
    public static final String EVENT_FEEDBACK_SUBMITTED = "FEEDBACK_SUBMITTED";
    public static final String EVENT_BC_UPDATE_SUCCESS = "BC_UPDATE_SUCCESS";
    public static final String EVENT_BC_UPDATE_FAILURE = "BC_UPDATE_FAILURE";
    public static final String EVENT_OCR_STARTED = "OCR_STARTED";
    public static final String EVENT_OCR_COMPLETED = "OCR_COMPLETED";
    public static final String EVENT_NLP_STARTED = "NLP_STARTED";
    public static final String EVENT_NLP_COMPLETED = "NLP_COMPLETED";
    public static final String EVENT_ERROR = "GENERAL_ERROR";


    @Autowired
    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Records an activity log entry.
     *
     * @param eventType    The type of event (use constants defined in this class).
     * @param description  A detailed description of the event.
     * @param relatedJobId Optional internal ID of the related job.
     * @param userIdentifier Optional identifier of the user performing the action.
     */
    public void recordActivity(String eventType, String description, Long relatedJobId, String userIdentifier) {
        try {
            ActivityLog logEntry = new ActivityLog(eventType, description, relatedJobId, userIdentifier);
            activityLogRepository.save(logEntry);
            log.debug("Recorded activity: Type={}, JobId={}, User={}, Desc={}", eventType, relatedJobId, userIdentifier, description);
        } catch (Exception e) {
            // Log the error but don't let logging failure break the main flow
            log.error("Failed to record activity log: Type={}, JobId={}, User={}, Desc={}",
                      eventType, relatedJobId, userIdentifier, description, e);
        }
    }

     /**
     * Retrieves the most recent activity logs, paginated.
     *
     * @param pageNumber The page number (0-based).
     * @param pageSize   The number of logs per page.
     * @return A Page of ActivityLog entities.
     */
    public Page<ActivityLog> getRecentActivities(int pageNumber, int pageSize) {
        log.debug("Fetching recent activities, page: {}, size: {}", pageNumber, pageSize);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("timestamp").descending());
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Retrieves all activity logs for a specific job.
     *
     * @param jobId The internal ID of the job.
     * @return A list of ActivityLog entities for the job.
     */
    public List<ActivityLog> getActivityForJob(Long jobId) {
        log.debug("Fetching activities for Job ID: {}", jobId);
        return activityLogRepository.findByRelatedJobIdOrderByTimestampDesc(jobId);
    }
}
