package com.erp.aierpbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyVerificationStatsDTO {
    private String date; // Format as needed (e.g., "YYYY-MM-DD" or "Mon")
    private long verified;
    private long flagged;
    private long pendingOrError; // Combine pending/processing/error for chart simplicity
}
