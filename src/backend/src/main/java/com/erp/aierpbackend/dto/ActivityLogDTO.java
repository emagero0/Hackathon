package com.erp.aierpbackend.dto;

import com.erp.aierpbackend.entity.ActivityLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogDTO {
    private Long id;
    private LocalDateTime timestamp;
    private String eventType;
    private String description;
    private Long relatedJobId;
    private String userIdentifier;

    // Static factory method to convert Entity to DTO
    public static ActivityLogDTO fromEntity(ActivityLog entity) {
        return new ActivityLogDTO(
                entity.getId(),
                entity.getTimestamp(),
                entity.getEventType(),
                entity.getDescription(),
                entity.getRelatedJobId(),
                entity.getUserIdentifier()
        );
    }
}
