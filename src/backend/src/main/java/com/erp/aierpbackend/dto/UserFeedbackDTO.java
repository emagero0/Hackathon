package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.UserFeedback;
import jakarta.validation.constraints.NotNull; // For input validation
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedbackDTO {

    private Long id; // Include ID for response

    @NotNull(message = "Job ID cannot be null")
    private Long jobId; // Internal Job ID

    @NotNull(message = "'isCorrect' flag cannot be null")
    private Boolean isCorrect;

    private String feedbackText;
    private String userIdentifier; // Optional for now
    private LocalDateTime feedbackTimestamp; // Include timestamp for response

    // Static factory method to convert Entity to DTO
    public static UserFeedbackDTO fromEntity(UserFeedback entity) {
        return new UserFeedbackDTO(
                entity.getId(),
                entity.getJob().getId(), // Get Job ID from the associated Job entity
                entity.getIsCorrect(),
                entity.getFeedbackText(),
                entity.getUserIdentifier(),
                entity.getFeedbackTimestamp()
        );
    }

    // Note: We don't need a toEntity method here as the service will handle entity creation
}
