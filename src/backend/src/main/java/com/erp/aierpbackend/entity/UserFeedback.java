package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; // Use Hibernate annotation for creation timestamp

import java.time.LocalDateTime;

@Entity
@Table(name = "user_feedback")
@Getter
@Setter
@NoArgsConstructor
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false) // Link feedback to a specific Job
    private Job job;

    // Could link to VerificationResult instead/as well if feedback is per-verification attempt
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "verification_result_id")
    // private VerificationResult verificationResult;

    @Column(name = "is_correct", nullable = false) // Was the AI verification correct according to the user?
    private Boolean isCorrect;

    @Column(name = "feedback_text", columnDefinition = "TEXT") // Optional user comments
    private String feedbackText;

    @Column(name = "user_identifier") // Optional: Store user ID or name if available later
    private String userIdentifier;

    @CreationTimestamp // Automatically set the timestamp when the entity is created
    @Column(name = "feedback_timestamp", nullable = false, updatable = false)
    private LocalDateTime feedbackTimestamp;

    // Constructor for easier creation
    public UserFeedback(Job job, Boolean isCorrect, String feedbackText, String userIdentifier) {
        this.job = job;
        this.isCorrect = isCorrect;
        this.feedbackText = feedbackText;
        this.userIdentifier = userIdentifier;
    }
}
