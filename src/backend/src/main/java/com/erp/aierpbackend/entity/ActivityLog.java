package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "event_type", nullable = false)
    private String eventType; // e.g., "JOB_PROCESSED", "FEEDBACK_SUBMITTED", "BC_UPDATED"

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description; // Detailed description of the event

    @Column(name = "related_job_id") // Optional: Link to the internal Job ID if applicable
    private Long relatedJobId;

    @Column(name = "user_identifier") // Optional: User associated with the action
    private String userIdentifier;

    // Constructor for easier creation
    public ActivityLog(String eventType, String description, Long relatedJobId, String userIdentifier) {
        this.eventType = eventType;
        this.description = description;
        this.relatedJobId = relatedJobId;
        this.userIdentifier = userIdentifier;
    }
}
