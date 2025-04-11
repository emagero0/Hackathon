package com.erp.aierpbackend.service;

import com.erp.aierpbackend.dto.UserFeedbackDTO;
import com.erp.aierpbackend.entity.Job;
import com.erp.aierpbackend.entity.UserFeedback;
import com.erp.aierpbackend.exception.ResourceNotFoundException; // Custom exception (to be created)
import com.erp.aierpbackend.repository.JobRepository;
import com.erp.aierpbackend.repository.UserFeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final UserFeedbackRepository feedbackRepository;
    private final JobRepository jobRepository;
    private final ActivityLogService activityLogService; // Inject ActivityLogService

    @Autowired
    public FeedbackService(UserFeedbackRepository feedbackRepository,
                           JobRepository jobRepository,
                           ActivityLogService activityLogService) { // Add ActivityLogService
        this.feedbackRepository = feedbackRepository;
        this.jobRepository = jobRepository;
        this.activityLogService = activityLogService; // Assign ActivityLogService
    }

    /**
     * Saves user feedback for a specific job.
     *
     * @param feedbackDTO DTO containing feedback details.
     * @return The saved UserFeedback entity converted back to DTO.
     * @throws ResourceNotFoundException if the Job with the given ID is not found.
     */
    @Transactional
    public UserFeedbackDTO saveFeedback(UserFeedbackDTO feedbackDTO) {
        log.info("Attempting to save feedback for Job ID: {}", feedbackDTO.getJobId());

        // Find the associated Job entity
        Job job = jobRepository.findById(feedbackDTO.getJobId())
                .orElseThrow(() -> {
                    log.warn("Job not found with ID: {} while trying to save feedback.", feedbackDTO.getJobId());
                    return new ResourceNotFoundException("Job", "id", feedbackDTO.getJobId());
                });

        // Create the UserFeedback entity from the DTO
        // For now, userIdentifier is taken directly from DTO, assuming it's provided externally
        UserFeedback feedback = new UserFeedback(
                job,
                feedbackDTO.getIsCorrect(),
                feedbackDTO.getFeedbackText(),
                feedbackDTO.getUserIdentifier() // This might be null if not provided
        );

        // Save the entity
        UserFeedback savedFeedback = feedbackRepository.save(feedback);
        log.info("Successfully saved feedback with ID: {} for Job ID: {}", savedFeedback.getId(), feedbackDTO.getJobId());

        // Record activity log
        // Assuming userIdentifier in DTO is the identifier of the user submitting feedback
        activityLogService.recordActivity(
                ActivityLogService.EVENT_FEEDBACK_SUBMITTED,
                String.format("User feedback submitted for Job ID %d. Correct: %s. Comment: %s",
                              feedbackDTO.getJobId(),
                              feedbackDTO.getIsCorrect(),
                              feedbackDTO.getFeedbackText() != null ? "'" + feedbackDTO.getFeedbackText() + "'" : "N/A"),
                feedbackDTO.getJobId(),
                feedbackDTO.getUserIdentifier() // Pass user identifier if available
        );

        // Convert back to DTO for the response
        return UserFeedbackDTO.fromEntity(savedFeedback);
    }

    /**
     * Retrieves all feedback entries for a specific job.
     *
     * @param jobId The internal ID of the job.
     * @return A list of UserFeedbackDTOs.
     */
    public List<UserFeedbackDTO> getFeedbackForJob(Long jobId) {
        log.debug("Fetching feedback for Job ID: {}", jobId);
        List<UserFeedback> feedbackList = feedbackRepository.findByJobId(jobId);
        return feedbackList.stream()
                .map(UserFeedbackDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
