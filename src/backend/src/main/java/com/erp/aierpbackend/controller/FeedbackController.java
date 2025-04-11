package com.erp.aierpbackend.controller;

import com.erp.aierpbackend.dto.UserFeedbackDTO;
import com.erp.aierpbackend.service.FeedbackService;
import jakarta.validation.Valid; // Import Valid annotation
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api") // Base path for API endpoints
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Endpoint to submit user feedback for a job.
     * @param feedbackDTO The feedback data.
     * @return The saved feedback DTO with generated ID and timestamp.
     */
    @PostMapping("/feedback")
    public ResponseEntity<UserFeedbackDTO> submitFeedback(@Valid @RequestBody UserFeedbackDTO feedbackDTO) {
        log.info("Received request to submit feedback for Job ID: {}", feedbackDTO.getJobId());
        // Assuming userIdentifier might be set based on authentication later
        // For MVP, it might be null or a placeholder from the frontend
        UserFeedbackDTO savedFeedback = feedbackService.saveFeedback(feedbackDTO);
        // Return 201 Created status with the saved feedback in the body
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFeedback);
    }

    /**
     * Endpoint to retrieve all feedback for a specific job.
     * @param jobId The internal ID of the job.
     * @return A list of feedback DTOs for the specified job.
     */
    @GetMapping("/jobs/{jobId}/feedback")
    public ResponseEntity<List<UserFeedbackDTO>> getFeedbackForJob(@PathVariable Long jobId) {
        log.info("Received request to get feedback for Job ID: {}", jobId);
        List<UserFeedbackDTO> feedbackList = feedbackService.getFeedbackForJob(jobId);
        log.info("Returning {} feedback entries for Job ID: {}", feedbackList.size(), jobId);
        return ResponseEntity.ok(feedbackList);
    }

    // We might add an exception handler here later to catch ResourceNotFoundException
    // from the service and return a proper 404 response.
}
